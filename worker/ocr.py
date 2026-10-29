"""OCR extraction module using native Windows.Media.Ocr with fallback."""
import io
import os
import subprocess
import tempfile
import logging
from typing import List, Optional
import fitz  # PyMuPDF

logger = logging.getLogger("notify.ocr")

# Path to the standalone PowerShell runner for Windows Media OCR
PS_OCR_SCRIPT = os.path.join(os.path.dirname(__file__), "win_ocr_runner.ps1")


def _ensure_ps_script():
    """Ensure the PowerShell OCR helper script exists."""
    if not os.path.exists(PS_OCR_SCRIPT):
        content = """param([string]$ImagePath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]

function Await($WinRtTask, $ResultType) {
    $netTask = $asTaskGeneric.MakeGenericMethod($ResultType).Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    $netTask.Result
}

[Windows.Globalization.Language, Windows.Globalization, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Ocr.OcrEngine, Windows.Foundation.UniversalApiContract, ContentType = WindowsRuntime] | Out-Null
[Windows.Graphics.Imaging.BitmapDecoder, Windows.Foundation.UniversalApiContract, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.StorageFile, Windows.Foundation.UniversalApiContract, ContentType = WindowsRuntime] | Out-Null

$lang = [Windows.Globalization.Language]::new("en-US")
$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage($lang)
if (-not $engine) {
    $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
}

$resolved = (Resolve-Path $ImagePath).Path
$file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($resolved)) ([Windows.Storage.StorageFile])
$stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
$decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
$softwareBitmap = Await ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])

$ocrResult = Await ($engine.RecognizeAsync($softwareBitmap)) ([Windows.Media.Ocr.OcrResult])
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Write-Output $ocrResult.Text
"""
        with open(PS_OCR_SCRIPT, "w", encoding="utf-8") as f:
            f.write(content)


def recognize_image_file(image_path: str) -> str:
    """Run OCR on a single image file via native Windows.Media.Ocr."""
    _ensure_ps_script()
    try:
        cmd = [
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", PS_OCR_SCRIPT,
            "-ImagePath", image_path
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30, encoding="utf-8")
        if result.returncode == 0:
            return result.stdout.strip()
        else:
            logger.warning(f"OCR PowerShell error: {result.stderr.strip()}")
            return ""
    except Exception as e:
        logger.error(f"Error executing OCR on image {image_path}: {e}")
        return ""


def extract_text_from_scanned_pdf(pdf_bytes: bytes, max_pages: int = 5) -> str:
    """
    Render PDF pages to high-DPI images and extract text using OCR.
    Handles scanned circulars, forms, and handwritten/printed examination notices.
    """
    if not pdf_bytes:
        return ""

    extracted_pages: List[str] = []
    tmp_dir = tempfile.gettempdir()

    try:
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
        pages_to_process = min(len(doc), max_pages)

        for i in range(pages_to_process):
            page = doc[i]
            # Render at 150 DPI for optimal speed and OCR accuracy
            pix = page.get_pixmap(dpi=150)
            img_path = os.path.join(tmp_dir, f"ocr_page_{os.getpid()}_{i}.png")
            pix.save(img_path)

            try:
                page_text = recognize_image_file(img_path)
                if page_text:
                    extracted_pages.append(page_text)
            finally:
                if os.path.exists(img_path):
                    try:
                        os.remove(img_path)
                    except Exception:
                        pass

        doc.close()
    except Exception as e:
        logger.error(f"Failed to process scanned PDF for OCR: {e}")

    return "\n\n--- Page Break ---\n\n".join(extracted_pages)
