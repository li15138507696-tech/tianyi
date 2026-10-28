Add-Type -AssemblyName System.Drawing

$root = (Resolve-Path 'art/legendary-gu').Path
$textureRoot = Join-Path $root 'textures/legendary_gu'
$modelTextureRoot = Join-Path $textureRoot 'model'
$bbRoot = Join-Path $root 'blockbench'
$jsonRoot = Join-Path $root 'models/item/legendary_gu'
$names = @('chunqiuchan','zhihuigu','lilianggu','suminggu')
$gridSize = 32
$cellSize = 0.5
$uvScale = 4

$display = [ordered]@{
  gui = [ordered]@{ rotation = @(30,225,0); translation = @(0,0,0); scale = @(0.72,0.72,0.72) }
  ground = [ordered]@{ rotation = @(0,0,0); translation = @(0,2,0); scale = @(0.32,0.32,0.32) }
  fixed = [ordered]@{ rotation = @(0,180,0); translation = @(0,0,0); scale = @(0.5,0.5,0.5) }
  thirdperson_righthand = [ordered]@{ rotation = @(0,90,55); translation = @(0,1.5,2.75); scale = @(0.5,0.5,0.5) }
  thirdperson_lefthand = [ordered]@{ rotation = @(0,90,55); translation = @(0,1.5,2.75); scale = @(0.5,0.5,0.5) }
  firstperson_righthand = [ordered]@{ rotation = @(0,-135,25); translation = @(0,3,1); scale = @(0.5,0.5,0.5) }
  firstperson_lefthand = [ordered]@{ rotation = @(0,-135,25); translation = @(0,3,1); scale = @(0.5,0.5,0.5) }
}

function Get-PartKey([string]$name, [double]$x, [double]$row, [string]$color) {
  # Keep the leaf-shaped cicada wings and jade-tinted Wisdom Gu wings anatomical.
  if ($name -eq 'chunqiuchan' -and $row -ge 3.5 -and $row -le 12.5) {
    if ($x -le 6.5) { return 'left_wing' }
    if ($x -ge 9) { return 'right_wing' }
  }
  # Wisdom Gu's broad jade wings are anatomy even though they use a jade palette.
  if ($name -eq 'zhihuigu' -and $row -ge 8 -and $row -le 12) {
    if ($x -le 5.5) { return 'left_wing' }
    if ($x -ge 10) { return 'right_wing' }
  }
  # Preserve Wisdom Gu's outer curved feelers where they pass through jade highlights.
  if ($name -eq 'zhihuigu' -and $row -ge 2.5 -and $row -le 5.5) {
    if ($x -ge 4.25 -and $x -le 6.25) { return 'feelers' }
    if ($x -ge 9.75 -and $x -le 11.75) { return 'feelers' }
  }
  if ($color -eq 'jade') {
    if (-not ($name -eq 'zhihuigu' -and $row -ge 4 -and $row -le 14 -and $x -ge 5 -and $x -le 10)) { return 'three_qi' }
  }
  switch ($name) {
    'chunqiuchan' {
      if ($row -ge 12 -and $x -ge 5 -and $x -le 10.5) { return 'legs' }
      if ($row -ge 4 -and $row -le 14 -and $x -ge 6 -and $x -le 9.5) { return 'wood_body' }
      return 'three_qi'
    }
    'zhihuigu' {
      if ($row -ge 7 -and $row -le 13.5 -and $x -ge 7 -and $x -le 9.5) { return 'wisdom_light' }
      if ($row -le 5.5 -and $x -ge 6 -and $x -le 9.5) { return 'feelers' }
      if ($row -ge 13 -and $x -ge 5 -and $x -le 10) { return 'legs' }
      if ($row -ge 4 -and $row -le 14.5 -and $x -ge 5 -and $x -le 10.5) { return 'insect_body' }
      if ($row -ge 5 -and $row -le 13 -and (($x -ge 3 -and $x -le 5) -or ($x -ge 11 -and $x -le 13))) { return 'feelers' }
      return 'three_qi'
    }
    'lilianggu' {
      if ($row -ge 5 -and $row -le 15.5 -and (($x -ge 2 -and $x -le 5.5) -or ($x -ge 10 -and $x -le 13.5))) { return 'legs' }
      if ($row -ge 4 -and $row -le 14 -and $x -ge 5 -and $x -le 10.5) {
        if ($row -ge 8 -and $row -le 14 -and $x -ge 7 -and $x -le 9) { return 'core' }
        return 'armored_body'
      }
      return 'three_qi'
    }
    'suminggu' {
      if ($color -eq 'red') { return 'wound' }
      if ($row -ge 9 -and $row -le 14.5 -and $x -ge 5.5 -and $x -le 10.5) { return 'abdomen' }
      if ($row -ge 5 -and $row -lt 9 -and $x -ge 5.5 -and $x -le 10.5) { return 'cephalothorax' }
      if ($row -ge 1 -and $row -lt 5 -and $x -ge 2 -and $x -le 6.5) { return 'left_legs' }
      if ($row -ge 1 -and $row -lt 5 -and $x -ge 8.5 -and $x -le 13) { return 'right_legs' }
      if ($row -ge 5 -and $row -le 14.5 -and $x -ge 2 -and $x -le 5.5) { return 'left_legs' }
      if ($row -ge 5 -and $row -le 14.5 -and $x -ge 10.5 -and $x -le 13.5) { return 'right_legs' }
      return 'three_qi'
    }
  }
}

function Get-PartLabel([string]$name, [string]$part) {
  $labels = @{
    chunqiuchan = @{ left_wing = '左枯叶翅 | Left Leaf Wing'; right_wing = '右枯叶翅 | Right Leaf Wing'; wood_body = '木质虫体 | Wooden Body'; legs = '足部 | Legs'; three_qi = '三气灵光 | Three-Qi Wisps' }
    zhihuigu = @{ feelers = '触须 | Feelers'; left_wing = '左翼 | Left Wing'; right_wing = '右翼 | Right Wing'; wisdom_light = '智慧光核 | Wisdom Light Core'; insect_body = '原创虫体 | Original Insect Body'; legs = '步足 | Legs'; three_qi = '三气灵光 | Three-Qi Wisps' }
    lilianggu = @{ armored_body = '甲壳虫体 | Armored Body'; core = '力量核心 | Strength Core'; legs = '步足 | Legs'; three_qi = '三气灵光 | Three-Qi Wisps' }
    suminggu = @{ cephalothorax = '头胸部 | Cephalothorax'; abdomen = '腹部 | Abdomen'; left_legs = '左侧蛛足 | Left Spider Legs'; right_legs = '右侧蛛足 | Right Spider Legs'; wound = '赤色伤痕 | Red Wound'; three_qi = '三气灵光 | Three-Qi Wisps' }
  }
  return $labels[$name][$part]
}

function Get-PartOrder([string]$name) {
  switch ($name) {
    'chunqiuchan' { return @('left_wing','right_wing','wood_body','legs','three_qi') }
    'zhihuigu' { return @('feelers','left_wing','right_wing','insect_body','wisdom_light','legs','three_qi') }
    'lilianggu' { return @('legs','armored_body','core','three_qi') }
    'suminggu' { return @('left_legs','right_legs','cephalothorax','abdomen','wound','three_qi') }
  }
}

function Get-PartDepth([string]$name, [string]$part) {
  switch ($part) {
    { $_ -in 'left_wing','right_wing','feelers','legs','left_legs','right_legs' } { return @(6,8.5) }
    'three_qi' { return @(7.5,8.5) }
    'wound' { return @(9.5,10.5) }
    'wisdom_light' { return @(9,10.2) }
    'core' { return @(7,10.2) }
    { $_ -in 'wood_body','insect_body','armored_body','cephalothorax','abdomen' } { return @(6.5,10) }
    default { return @(7,9) }
  }
}

foreach ($name in $names) {
  $pngPath = Join-Path $modelTextureRoot ($name + '.png')
  $bitmap = [System.Drawing.Bitmap]::new($pngPath)
  if ($bitmap.Width -ne 128 -or $bitmap.Height -ne 128) { throw "$name model texture must be 128x128" }

  # Reduce the 128x128 model texture to a 32x32 geometry occupancy mask. Each half-unit
  # geometry cell maps to a 4x4 UV tile, retaining finer anatomy while keeping crisp textures.
  $active = @{}
  $segments = [System.Collections.Generic.List[object]]::new()
  for ($row = 0; $row -lt $gridSize; $row++) {
    $rowRuns = [System.Collections.Generic.List[object]]::new()
    $runStart = -1
    $runPart = ''
    for ($x = 0; $x -le $gridSize; $x++) {
      $occupied = $false
      $color = 'other'
      $part = ''
      if ($x -lt $gridSize) {
        $opaqueCount = 0
        $jadeCount = 0
        $redCount = 0
        for ($ty = $row * $uvScale; $ty -lt ($row + 1) * $uvScale; $ty++) {
          for ($tx = $x * $uvScale; $tx -lt ($x + 1) * $uvScale; $tx++) {
            $pixel = $bitmap.GetPixel($tx,$ty)
            if ($pixel.A -ge 128) {
              $opaqueCount++
              if ($pixel.G -gt ($pixel.R * 1.2) -and $pixel.G -gt ($pixel.B * 0.9)) { $jadeCount++ }
              if ($pixel.R -gt 150 -and $pixel.R -gt ($pixel.G * 1.35) -and $pixel.R -gt ($pixel.B * 1.3)) { $redCount++ }
            }
          }
        }
        if ($opaqueCount -ge 4) {
          $occupied = $true
          if ($redCount -ge [Math]::Max(2,[Math]::Ceiling($opaqueCount * 0.25))) { $color = 'red' }
          elseif ($jadeCount -ge [Math]::Max(3,[Math]::Ceiling($opaqueCount * 0.3))) { $color = 'jade' }
          $part = Get-PartKey $name (($x + 0.5) * $cellSize) (($row + 0.5) * $cellSize) $color
        }
      }
      if (-not $occupied -or $part -ne $runPart) {
        if ($runStart -ge 0) {
          $rowRuns.Add([pscustomobject]@{ row0 = $row; row1 = $row + 1; x0 = $runStart; x1 = $x; part = $runPart })
          $runStart = -1
          $runPart = ''
        }
        if ($occupied) { $runStart = $x; $runPart = $part }
      }
    }

    $nextActive = @{}
    foreach ($run in $rowRuns) {
      $runKey = "$($run.x0):$($run.x1):$($run.part)"
      if ($active.ContainsKey($runKey) -and $active[$runKey].row1 -eq $row) {
        $active[$runKey].row1 = $row + 1
        $nextActive[$runKey] = $active[$runKey]
      } else {
        $nextActive[$runKey] = $run
      }
    }
    foreach ($oldKey in @($active.Keys)) {
      if (-not $nextActive.ContainsKey($oldKey)) { $segments.Add($active[$oldKey]) }
    }
    $active = $nextActive
  }
  foreach ($leftover in $active.Values) { $segments.Add($leftover) }
  $bitmap.Dispose()

  $bbElements = [System.Collections.Generic.List[object]]::new()
  $itemElements = [System.Collections.Generic.List[object]]::new()
  $partChildren = @{}
  $partCounts = @{}
  $cubeIndex = 0
  foreach ($segment in $segments) {
    if (-not $partChildren.ContainsKey($segment.part)) { $partChildren[$segment.part] = [System.Collections.Generic.List[string]]::new() }
    $uuid = [guid]::NewGuid().ToString()
    $row0 = [int]$segment.row0
    $row1 = [int]$segment.row1
    $x0 = [int]$segment.x0
    $x1 = [int]$segment.x1
    $y0 = 16 - ($row1 * $cellSize)
    $y1 = 16 - ($row0 * $cellSize)
    $depth = Get-PartDepth $name $segment.part
    $z0 = [double]$depth[0]
    $z1 = [double]$depth[1]
    $frontUv = @(($x0 * $uvScale),($row0 * $uvScale),($x1 * $uvScale),($row1 * $uvScale))
    $edgeUv = @(($x0 * $uvScale),($row0 * $uvScale),(($x0 + 1) * $uvScale),(($row0 + 1) * $uvScale))
    $frontItemUv = @(($x0 * $cellSize),($row0 * $cellSize),($x1 * $cellSize),($row1 * $cellSize))
    $edgeItemUv = @(($x0 * $cellSize),($row0 * $cellSize),(($x0 + 1) * $cellSize),(($row0 + 1) * $cellSize))
    $bbFaces = [ordered]@{}
    $itemFaces = [ordered]@{}
    foreach ($direction in @('north','south','east','west','up','down')) {
      if ($direction -eq 'north') {
        $bbUv = @($frontUv[2],$frontUv[1],$frontUv[0],$frontUv[3])
        $itemUv = @($frontItemUv[2],$frontItemUv[1],$frontItemUv[0],$frontItemUv[3])
      } elseif ($direction -eq 'south') {
        $bbUv = $frontUv
        $itemUv = $frontItemUv
      } else {
        $bbUv = $edgeUv
        $itemUv = $edgeItemUv
      }
      $bbFaces[$direction] = [ordered]@{ uv = $bbUv; texture = 0 }
      $itemFaces[$direction] = [ordered]@{ uv = $itemUv; texture = '#layer0' }
    }
    $bbElements.Add([ordered]@{
      name = ('{0}_{1:D2}_{2:D2}' -f $segment.part,$row0,$cubeIndex)
      rescale = $false
      locked = $false
      from = @(($x0 * $cellSize),$y0,$z0)
      to = @(($x1 * $cellSize),$y1,$z1)
      autouv = 0
      color = 0
      origin = @(8,8,8)
      faces = $bbFaces
      type = 'cube'
      uuid = $uuid
    })
    $itemElements.Add([ordered]@{
      from = @(($x0 * $cellSize),$y0,$z0)
      to = @(($x1 * $cellSize),$y1,$z1)
      faces = $itemFaces
    })
    $partChildren[$segment.part].Add($uuid)
    if (-not $partCounts.ContainsKey($segment.part)) { $partCounts[$segment.part] = 0 }
    $partCounts[$segment.part]++
    $cubeIndex++
  }

  $outliner = [System.Collections.Generic.List[object]]::new()
  $groupIndex = 0
  foreach ($part in (Get-PartOrder $name)) {
    if (-not $partChildren.ContainsKey($part) -or $partChildren[$part].Count -eq 0) { continue }
    $outliner.Add([ordered]@{
      name = Get-PartLabel $name $part
      origin = @(8,8,8)
      color = $groupIndex % 8
      uuid = [guid]::NewGuid().ToString()
      children = @($partChildren[$part].ToArray())
      export = $true
      isOpen = $false
      locked = $false
      visibility = $true
    })
    $groupIndex++
  }

  $resource = 'tianyi:item/legendary_gu/model/' + $name
  $itemModel = [ordered]@{
    ambientocclusion = $true
    gui_light = 'front'
    textures = [ordered]@{ layer0 = $resource; particle = $resource }
    elements = @($itemElements.ToArray())
    display = $display
  }
  $itemJson = $itemModel | ConvertTo-Json -Depth 100
  [System.IO.File]::WriteAllText((Join-Path $jsonRoot ($name + '.json')),$itemJson,[System.Text.UTF8Encoding]::new($false))

  $pngBytes = [System.IO.File]::ReadAllBytes($pngPath)
  $texture = [ordered]@{
    path = ('../textures/legendary_gu/model/' + $name + '.png')
    name = $name
    folder = 'item/legendary_gu/model'
    namespace = 'tianyi'
    id = '0'
    particle = $true
    render_mode = 'default'
    visible = $true
    mode = 'bitmap'
    saved = $false
    uuid = [guid]::NewGuid().ToString()
    source = 'data:image/png;base64,' + [Convert]::ToBase64String($pngBytes)
  }
  $bbModel = [ordered]@{
    meta = [ordered]@{ format_version = '4.0'; model_format = 'java_block'; box_uv = $false }
    name = $name
    parent = ''
    ambientocclusion = $true
    front_gui_light = $true
    visible_box = @(1,1,0)
    variable_placeholders = ''
    variable_placeholder_buttons = @()
    resolution = [ordered]@{ width = 128; height = 128 }
    elements = @($bbElements.ToArray())
    outliner = @($outliner.ToArray())
    textures = @($texture)
    display = $display
  }
  $bbJson = $bbModel | ConvertTo-Json -Depth 100
  [System.IO.File]::WriteAllText((Join-Path $bbRoot ($name + '.bbmodel')),$bbJson,[System.Text.UTF8Encoding]::new($false))
  [pscustomobject]@{ Name = $name; ModelTexture = '128x128'; LowPolyPartGroups = $outliner.Count; PartCounts = ($partCounts | ConvertTo-Json -Compress); Elements = $cubeIndex; TextureBytes = $pngBytes.Length }
}
