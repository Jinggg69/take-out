param(
    [ValidateSet('test', 'package', 'verify')]
    [string]$Goal = 'test',
    [string]$MavenPath,
    [string]$RepositoryPath,
    [string]$Test,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskProject = Join-Path $taskRoot 'backend\junzhou-take-out'

if (!$MavenPath) {
    $taskCommand = Get-Command mvn.cmd,mvn -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($taskCommand) { $MavenPath = $taskCommand.Source }
}
if (!$MavenPath -and $env:MAVEN_HOME) {
    $MavenPath = Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'
}
if (!$MavenPath -and $env:ProgramFiles) {
    # Use an existing IDE-bundled Maven on Windows; never download or install one silently.
    $taskJetBrains = Join-Path $env:ProgramFiles 'JetBrains'
    if (Test-Path -LiteralPath $taskJetBrains) {
        $MavenPath = Get-ChildItem -LiteralPath $taskJetBrains -Directory |
            Where-Object Name -Like 'IntelliJ IDEA*' | Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'plugins\maven\lib\maven3\bin\mvn.cmd' } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
    }
}
if (!$MavenPath -or !(Test-Path -LiteralPath $MavenPath -PathType Leaf)) {
    throw 'Maven not found. Add Maven to PATH, set MAVEN_HOME, or provide -MavenPath.'
}
if (!$RepositoryPath -and $env:MAVEN_REPO_LOCAL) { $RepositoryPath = $env:MAVEN_REPO_LOCAL }
if (!$RepositoryPath -and $env:USERPROFILE) {
    $taskCachedRepository = Join-Path $env:USERPROFILE '.m2\repository'
    if (Test-Path -LiteralPath $taskCachedRepository -PathType Container) { $RepositoryPath = $taskCachedRepository }
}

$taskArguments = @('-B', '-ntp', '-pl', 'junzhou-server', '-am')
if ($Offline) { $taskArguments += '-o' }
if ($RepositoryPath) { $taskArguments += "-Dmaven.repo.local=$RepositoryPath" }
if ($Test) { $taskArguments += "-Dtest=$Test", '-Dsurefire.failIfNoSpecifiedTests=false' }
$taskArguments += $Goal
Write-Output "Maven: $MavenPath"
Write-Output "Project: $taskProject"
if ($RepositoryPath) { Write-Output "Local repository: $RepositoryPath" }
Push-Location -LiteralPath $taskProject
try {
    & $MavenPath @taskArguments
    $taskExit = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $taskExit
