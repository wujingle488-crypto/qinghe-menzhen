param([Parameter(Mandatory = $true)][string]$Path)
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Add-Type -AssemblyName System.Speech
$culture = [System.Globalization.CultureInfo]::new("zh-CN")
$engine = New-Object System.Speech.Recognition.SpeechRecognitionEngine $culture
try {
    $engine.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar))
    $engine.SetInputToWaveFile($Path)
    $engine.InitialSilenceTimeout = [TimeSpan]::FromSeconds(2)
    $engine.BabbleTimeout = [TimeSpan]::FromSeconds(3)
    $engine.EndSilenceTimeout = [TimeSpan]::FromMilliseconds(800)
    $result = $engine.Recognize()
    if ($null -ne $result -and $result.Text) {
        Write-Output $result.Text
    }
} finally {
    $engine.Dispose()
}
