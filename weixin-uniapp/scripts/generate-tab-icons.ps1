$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$taskOutput = Join-Path (Split-Path -Parent $PSScriptRoot) 'static/tabbar'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
foreach ($taskName in @('home','menu','orders','user')) {
    foreach ($taskActive in @($false,$true)) {
        $taskBitmap = New-Object Drawing.Bitmap 192,192
        $taskGraphics = [Drawing.Graphics]::FromImage($taskBitmap)
        $taskGraphics.Clear([Drawing.Color]::Transparent)
        $taskGraphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $taskGraphics.ScaleTransform(3,3)
        $taskHex = if ($taskActive) { '#176B4B' } else { '#647169' }
        $taskPen = New-Object Drawing.Pen ([Drawing.ColorTranslator]::FromHtml($taskHex)),2.8
        $taskPen.StartCap = [Drawing.Drawing2D.LineCap]::Round
        $taskPen.EndCap = [Drawing.Drawing2D.LineCap]::Round
        $taskPen.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
        switch ($taskName) {
            'home' {
                $taskPoints = [Drawing.PointF[]]@([Drawing.PointF]::new(9,29),[Drawing.PointF]::new(32,10),[Drawing.PointF]::new(55,29))
                $taskGraphics.DrawLines($taskPen,$taskPoints)
                $taskGraphics.DrawLines($taskPen,[Drawing.PointF[]]@([Drawing.PointF]::new(15,27),[Drawing.PointF]::new(15,53),[Drawing.PointF]::new(26,53),[Drawing.PointF]::new(26,37),[Drawing.PointF]::new(38,37),[Drawing.PointF]::new(38,53),[Drawing.PointF]::new(49,53),[Drawing.PointF]::new(49,27)))
            }
            'menu' {
                $taskGraphics.DrawArc($taskPen,13,22,38,38,180,180)
                $taskGraphics.DrawLine($taskPen,10,43,54,43)
                $taskGraphics.DrawLine($taskPen,14,51,50,51)
                $taskGraphics.DrawLine($taskPen,32,17,32,21)
                $taskGraphics.DrawArc($taskPen,28,9,8,8,0,360)
            }
            'orders' {
                $taskGraphics.DrawLines($taskPen,[Drawing.PointF[]]@([Drawing.PointF]::new(18,10),[Drawing.PointF]::new(46,10),[Drawing.PointF]::new(46,54),[Drawing.PointF]::new(39,50),[Drawing.PointF]::new(32,54),[Drawing.PointF]::new(25,50),[Drawing.PointF]::new(18,54),[Drawing.PointF]::new(18,10)))
                foreach ($taskY in @(23,32,41)) { $taskGraphics.DrawLine($taskPen,25,$taskY,39,$taskY) }
            }
            'user' {
                $taskGraphics.DrawEllipse($taskPen,23,9,18,18)
                $taskGraphics.DrawArc($taskPen,14,33,36,36,180,180)
                $taskGraphics.DrawLine($taskPen,14,51,50,51)
            }
        }
        $taskSmall = New-Object Drawing.Bitmap 64,64
        $taskResize = [Drawing.Graphics]::FromImage($taskSmall)
        $taskResize.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $taskResize.DrawImage($taskBitmap,0,0,64,64)
        $taskSuffix = if ($taskActive) { '-active' } else { '' }
        $taskSmall.Save((Join-Path $taskOutput ($taskName+$taskSuffix+'.png')),[Drawing.Imaging.ImageFormat]::Png)
        $taskResize.Dispose(); $taskSmall.Dispose(); $taskPen.Dispose(); $taskGraphics.Dispose(); $taskBitmap.Dispose()
    }
}
Write-Output 'Generated eight native tabBar icons.'
