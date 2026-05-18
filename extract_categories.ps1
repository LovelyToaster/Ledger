$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$xlsPath = Join-Path $scriptDir "账单_0509185644.xls"
$csvPath = Join-Path $scriptDir "categories_output.csv"

$conn = New-Object System.Data.Odbc.OdbcConnection(
    "Driver={Microsoft Excel Driver (*.xls, *.xlsx, *.xlsm, *.xlsb)};DBQ=$xlsPath;ReadOnly=1;"
)
$conn.Open()

$tables = $conn.GetSchema("Tables")
$tableName = $tables[0].TABLE_NAME

$cmd = $conn.CreateCommand()
$cmd.CommandText = "SELECT * FROM [$tableName]"
$reader = $cmd.ExecuteReader()

$data = [System.Collections.Generic.List[PSObject]]::new()

$reader.Read() | Out-Null

while ($reader.Read()) {
    $cat  = $reader.GetValue(3)
    $sub  = $reader.GetValue(4)
    $note = $reader.GetValue(9)

    if ($cat -or $sub -or $note) {
        $data.Add([PSCustomObject]@{
            类别       = $cat
            二级分类   = $sub
            备注       = $note
        })
    }
}

$reader.Close()
$conn.Close()

$unique = $data | Sort-Object 类别, 二级分类, 备注 -Unique

$unique | Export-Csv -LiteralPath $csvPath -Encoding UTF8 -NoTypeInformation

Write-Output "总数据行: $($data.Count)"
Write-Output "去重后行: $($unique.Count)"
Write-Output "输出文件: $csvPath"
