$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$directory = Join-Path $root 'branding\options'
$entries = @(
    @{ File = '01-bagli-kalpler.png'; Title = '1  Bağlı kalpler'; Detail = 'İki ayrı kalp, tek bağ.' },
    @{ File = '02-sonsuz-bag.png'; Title = '2  Sonsuz bağ'; Detail = 'Kalbin içinde süreklilik.' },
    @{ File = '03-sarilma.png'; Title = '3  Sarılma'; Detail = 'Birbirine yaklaşan iki insan.' },
    @{ File = '04-kalp-dugumu.png'; Title = '4  Kalp düğümü'; Detail = 'Birbirine dolanan iki yol.' }
)
$bitmap = New-Object Drawing.Bitmap(1600, 720)
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$graphics.Clear([Drawing.ColorTranslator]::FromHtml('#110F15'))
$foreground = New-Object Drawing.SolidBrush([Drawing.ColorTranslator]::FromHtml('#FFF1E5'))
$secondary = New-Object Drawing.SolidBrush([Drawing.ColorTranslator]::FromHtml('#BEAFBE'))
$card = New-Object Drawing.SolidBrush([Drawing.ColorTranslator]::FromHtml('#201A24'))
$headingFont = New-Object Drawing.Font('Segoe UI', 32, [Drawing.FontStyle]::Bold)
$titleFont = New-Object Drawing.Font('Segoe UI', 20, [Drawing.FontStyle]::Bold)
$bodyFont = New-Object Drawing.Font('Segoe UI', 15)
try {
    $graphics.DrawString('Bağımlılığım', $headingFont, $foreground, 42, 24)
    $graphics.DrawString('Alışkanlıklardan daha güçlü bağlar.', $bodyFont, $secondary, 46, 82)
    for ($i = 0; $i -lt $entries.Count; $i++) {
        $entry = $entries[$i]
        $x = 40 + $i * 390
        $graphics.FillRectangle($card, $x, 140, 370, 525)
        $logo = [Drawing.Image]::FromFile((Join-Path $directory $entry.File))
        try {
            $graphics.DrawImage($logo, $x + 35, 166, 300, 300)
            $graphics.DrawImage($logo, $x + 35, 574, 48, 48)
        } finally { $logo.Dispose() }
        $graphics.DrawString($entry.Title, $titleFont, $foreground, $x + 24, 486)
        $graphics.DrawString($entry.Detail, $bodyFont, $secondary, $x + 25, 528)
        $graphics.DrawString('Küçük simge', $bodyFont, $secondary, $x + 99, 584)
    }
    $bitmap.Save((Join-Path $directory 'logo-secenekleri.png'), [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $headingFont.Dispose(); $titleFont.Dispose(); $bodyFont.Dispose()
    $foreground.Dispose(); $secondary.Dispose(); $card.Dispose()
    $graphics.Dispose(); $bitmap.Dispose()
}
Write-Output (Join-Path $directory 'logo-secenekleri.png')
