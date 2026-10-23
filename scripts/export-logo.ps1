param(
    [string]$VectorPath,
    [string]$OutputDirectory,
    [string]$SvgName = 'bagimliligim.svg',
    [string]$PngName = 'play-store-icon-512.png'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $projectRoot 'branding' }
if (-not $VectorPath) { $VectorPath = Join-Path $projectRoot 'app\src\main\res\drawable\ic_bond.xml' }
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
[xml]$vector = Get-Content -LiteralPath $VectorPath -Encoding UTF8
$namespace = 'http://schemas.android.com/apk/res/android'
$bitmap = New-Object Drawing.Bitmap(1024, 1024)
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([Drawing.ColorTranslator]::FromHtml('#291B2C'))
$graphics.ScaleTransform([single](1024 / 108), [single](1024 / 108))
$svg = New-Object Text.StringBuilder
[void]$svg.AppendLine('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512"><title>Bagimliligim</title><rect width="108" height="108" fill="#291B2C"/>')

try {
    foreach ($node in $vector.vector.path) {
        $data = $node.GetAttribute('pathData', $namespace)
        $fill = $node.GetAttribute('fillColor', $namespace)
        $stroke = $node.GetAttribute('strokeColor', $namespace)
        $width = $node.GetAttribute('strokeWidth', $namespace)
        $cap = $node.GetAttribute('strokeLineCap', $namespace)
        if ($fill -eq '#00000000') { $fill = 'none' }
        if (-not $stroke) { $stroke = 'none' }
        if (-not $width) { $width = '0' }
        if (-not $cap) { $cap = 'butt' }
        [void]$svg.AppendLine(('<path d="{0}" fill="{1}" stroke="{2}" stroke-width="{3}" stroke-linecap="{4}" stroke-linejoin="round"/>' -f $data, $fill, $stroke, $width, $cap))
        $tokens = [regex]::Matches($data, '[A-Za-z]|-?\d+(?:\.\d+)?')
        $path = New-Object Drawing.Drawing2D.GraphicsPath
        $index = 0
        [single]$x = 0
        [single]$y = 0
        [single]$startX = 0
        [single]$startY = 0
        try {
            while ($index -lt $tokens.Count) {
                $command = $tokens[$index++].Value
                $coordinateCount = switch ($command) { 'M' { 2 } 'L' { 2 } 'C' { 6 } 'Z' { 0 } default { throw "Unsupported vector command: $command" } }
                $coordinates = @()
                for ($coordinate = 0; $coordinate -lt $coordinateCount; $coordinate++) {
                    $coordinates += [single]::Parse($tokens[$index++].Value, [Globalization.CultureInfo]::InvariantCulture)
                }
                switch ($command) {
                    'M' { $path.StartFigure(); $x = $coordinates[0]; $y = $coordinates[1]; $startX = $x; $startY = $y }
                    'L' { $path.AddLine($x, $y, $coordinates[0], $coordinates[1]); $x = $coordinates[0]; $y = $coordinates[1] }
                    'C' { $path.AddBezier($x, $y, $coordinates[0], $coordinates[1], $coordinates[2], $coordinates[3], $coordinates[4], $coordinates[5]); $x = $coordinates[4]; $y = $coordinates[5] }
                    'Z' { $path.CloseFigure(); $x = $startX; $y = $startY }
                }
            }
            if ($fill -ne 'none') {
                $brush = New-Object Drawing.SolidBrush([Drawing.ColorTranslator]::FromHtml($fill))
                try { $graphics.FillPath($brush, $path) } finally { $brush.Dispose() }
            }
            if ($stroke -ne 'none') {
                $pen = New-Object Drawing.Pen([Drawing.ColorTranslator]::FromHtml($stroke), [single]::Parse($width, [Globalization.CultureInfo]::InvariantCulture))
                try {
                    $pen.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
                    if ($cap -eq 'round') { $pen.StartCap = [Drawing.Drawing2D.LineCap]::Round; $pen.EndCap = [Drawing.Drawing2D.LineCap]::Round }
                    $graphics.DrawPath($pen, $path)
                } finally { $pen.Dispose() }
            }
        } finally { $path.Dispose() }
    }
    [void]$svg.AppendLine('</svg>')
    [IO.File]::WriteAllText((Join-Path $outputDirectory $SvgName), $svg.ToString(), (New-Object Text.UTF8Encoding($false)))
    $icon = New-Object Drawing.Bitmap(512, 512)
    $iconGraphics = [Drawing.Graphics]::FromImage($icon)
    try {
        $iconGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $iconGraphics.DrawImage($bitmap, 0, 0, 512, 512)
        $icon.Save((Join-Path $outputDirectory $PngName), [Drawing.Imaging.ImageFormat]::Png)
    } finally { $iconGraphics.Dispose(); $icon.Dispose() }
} finally { $graphics.Dispose(); $bitmap.Dispose() }
Write-Output "Branding assets: $outputDirectory"
