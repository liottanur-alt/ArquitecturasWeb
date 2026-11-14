#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [ValidateRange(1,100)][int]$Concurrency = 10,
    [ValidateRange(10,100000)][int]$Requests = 500,
    [ValidateRange(1,60000)][double]$MaxP95Ms = 2000,
    [ValidateRange(0,1)][double]$MaxErrorRate = 0.01,
    [string]$FixturePath = "$PSScriptRoot/../../.local/system-tests/integration-security.json",
    [string]$OutputDirectory = "$PSScriptRoot/../../.local/system-tests"
)
. "$PSScriptRoot/Common.ps1"
$BaseUrl = $BaseUrl.TrimEnd('/')
$fixtureReport = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
if ($fixtureReport.baseUrl -ne $BaseUrl) { throw 'El fixture pertenece a otro servidor.' }
if (-not $fixtureReport.passed) { throw 'Ejecutar primero Test-System.ps1 con integracion exitosa.' }
$paths = @('/api/eventos', $fixtureReport.fixture.availabilityPath, "/compras/cliente/$($fixtureReport.fixture.CLIENTE.id)")
# Calentamiento fuera de las mediciones; detener si el entorno no esta disponible.
foreach ($path in $paths) { Invoke-Api GET $path | Out-Null }
$timer = [Diagnostics.Stopwatch]::StartNew()
$samples = @(1..$Requests | ForEach-Object -Parallel {
    $pathsLocal = $using:paths
    $path = $pathsLocal[($_ - 1) % $pathsLocal.Count]
    $base = $using:BaseUrl
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $status = 0
    $errorText = $null
    try {
        $response = Invoke-WebRequest -Uri "$base$path" -SkipHttpErrorCheck -TimeoutSec 30
        $status = [int]$response.StatusCode
        $null = $response.Content | ConvertFrom-Json -ErrorAction Stop
    } catch { $errorText = $_.Exception.Message }
    $watch.Stop()
    [pscustomobject]@{path = $path; status = $status; ms = $watch.Elapsed.TotalMilliseconds; passed = ($status -eq 200 -and $null -eq $errorText); error = $errorText}
} -ThrottleLimit $Concurrency)
$timer.Stop()
function Get-Metrics($Items) {
    $itemsArray = @($Items)
    $sorted = @($itemsArray.ms | Sort-Object)
    $errors = @($itemsArray | Where-Object { -not $_.passed }).Count
    $p95 = $sorted[[Math]::Max(0, [Math]::Ceiling($sorted.Count * 0.95) - 1)]
    @{requests = $itemsArray.Count; errors = $errors; errorRate = $errors / $itemsArray.Count; p50Ms = $sorted[[Math]::Max(0,[Math]::Ceiling($sorted.Count * 0.50)-1)]; p95Ms = $p95; maxMs = $sorted[-1]; passed = ($p95 -le $MaxP95Ms -and $errors / $itemsArray.Count -le $MaxErrorRate)}
}
$metrics = Get-Metrics $samples
$perEndpoint = @($samples | Group-Object path | ForEach-Object { @{path = $_.Name; metrics = (Get-Metrics $_.Group)} })
$passed = $metrics.passed -and @($perEndpoint | Where-Object { -not $_.metrics.passed }).Count -eq 0
$report = @{finishedAt = [DateTimeOffset]::UtcNow.ToString('o'); baseUrl = $BaseUrl; concurrency = $Concurrency; durationSeconds = $timer.Elapsed.TotalSeconds; requestsPerSecond = $Requests / $timer.Elapsed.TotalSeconds; thresholds = @{maxP95Ms = $MaxP95Ms; maxErrorRate = $MaxErrorRate}; metrics = $metrics; endpoints = $perEndpoint; passed = $passed; samples = $samples}
Save-Report $report "$OutputDirectory/load.json"
$metrics | Format-List
Write-Host "Solicitudes/s: $($report.requestsPerSecond); informe: $OutputDirectory/load.json"
if (-not $passed) { exit 1 }
