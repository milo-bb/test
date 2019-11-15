■テストデータ作成vba
Option Explicit
Dim ws As Worksheet

Sub 改行ありデータファイル作成()
    
    Set ws = ThisWorkbook.Worksheets("改行ありデータ")
    
    Dim dt1 As String
    dt1 = Mid(日付(13, 3, 8), 1, 4) & "/" & Mid(日付(13, 3, 8), 5, 2) & "/" & Mid(日付(13, 3, 8), 7, 2)

    Dim datFile As String
    datFile = ActiveWorkbook.Path & "\ファイル名_" & Format(Now, "YYYYMMDDHHNNSS")
 
    Open datFile For Output As #1
    
    '======ヘッダー出力======
    Print #1, "FFFF300200101" & Format(DateAdd("d", -1, dt1), "YYYYMMDD") & "000000" & Format(レコード件数取得(13, 4), "00000000") & String(1508, " ") & vbLf;
 
    '========明細出力========
    Print #1, ファイル作成_明細出力(vbLf, 1);
       
    '======フッター出力======
    Print #1, "FFFF300200103" & Format(DateAdd("d", -1, dt1), "YYYYMMDD") & "000000" & String(1516, " ") & vbLf;
 
    Close #1
    
    '======crlファイル作成=======
    Call Ctlファイル作成(datFile)
 
    MsgBox datFile
End Sub

Sub 改行なしファイル作成()
    
    Set ws = ThisWorkbook.Worksheets("改行なしファイル")
    
    Dim dt1 As String
    dt1 = Mid(日付(13, 3, 8), 1, 4) & "/" & Mid(日付(13, 3, 8), 5, 2) & "/" & Mid(日付(13, 3, 8), 7, 2)

    Dim datFile As String
    datFile = ActiveWorkbook.Path & "\ファイル名_" & Format(Now, "YYYYMMDDHHNNSS")
 
    Open datFile For Output As #1
    
    '======ヘッダー出力======
    Print #1, "1" & Format(DateAdd("d", -1, dt1), "YYYYMMDD") & "3059356010747" & "99" & String(176, " ");
 
    '========明細出力========
    Print #1, ファイル作成_明細出力("", 1);
       
    '======フッター出力======
    Print #1, "9" & Right("00000000" & レコード件数取得(13, 4), 8) & String(191, " ");
 
    Close #1
    
    '======crlファイル作成=======
    Call Ctlファイル作成(datFile)
 
    MsgBox datFile
End Sub

Sub タブ区切りファイル作成()
    
    Set ws = ThisWorkbook.Worksheets("タブ区切りファイル")
    
    Dim datFile As String
    datFile = ActiveWorkbook.Path & "\ファイル名_" & Format(Now, "YYYYMMDDHHNNSS")
 
    Open datFile For Output As #1
    
    '========明細出力========
    Print #1, ファイル作成_明細出力(vbLf, 2);
        
    Close #1
    
    '======crlファイル作成=======
    Call Ctlファイル作成(datFile)
 
    MsgBox datFile
End Sub


'i レコード行
'j レコード列
'm 左詰・右詰行行
'n パディング文字行
'h バイト数
Function ファイル作成_明細固定(ByVal i As Long, ByVal j As Long, ByVal m As Long, ByVal n As Long, ByVal h As Long) As String
        If ws.Cells(m, j).Value = "L" Then
            If ws.Cells(n, j).Value = "SP" Then
                ファイル作成_明細固定 = StrConv(LeftB(StrConv(ws.Cells(i, j).Value & String(ws.Cells(h, j).Value, " "), vbFromUnicode), ws.Cells(h, j).Value), vbUnicode)
            ElseIf ws.Cells(n, j).Value = "DSP" Then
                ファイル作成_明細固定 = StrConv(LeftB(StrConv(ws.Cells(i, j).Value & String(ws.Cells(h, j).Value, "　"), vbFromUnicode), ws.Cells(h, j).Value), vbUnicode)
            Else
                ファイル作成_明細固定 = StrConv(LeftB(StrConv(ws.Cells(i, j).Value & String(ws.Cells(h, j).Value, "0"), vbFromUnicode), ws.Cells(h, j).Value), vbUnicode)
            End If
        Else
            If ws.Cells(n, j).Value = "SP" Then
                ファイル作成_明細固定 = StrConv(RightB(StrConv(String(ws.Cells(h, j).Value, " ") & ws.Cells(i, j).Value, vbFromUnicode), ws.Cells(h, j).Value), vbUnicode)
            ElseIf ws.Cells(n, j).Value = "DSP" Then
                ファイル作成_明細固定 = StrConv(RightB(StrConv(String(ws.Cells(h, j).Value, "　") & ws.Cells(i, j).Value, vbFromUnicode), ws.Cells(h, j).Value), vbUnicode)
            Else
                ファイル作成_明細固定 = StrConv(RightB(StrConv(String(ws.Cells(h, j).Value, "0") & ws.Cells(i, j).Value, vbFromUnicode), ws.Cells(h, j).Value), vbUnicode)
            End If
        End If
End Function


'line 改行コード
'switch 明細長(1:固定、2:可変)
Function ファイル作成_明細出力(ByVal line As String, ByVal switch As Long) As String
     'レコード開始行
     Dim i As Long
     i = 13
     'レコード開始列
     Dim j As Long
     j = 4
     '左詰・右詰行
     Dim m As Long
     m = 11
     'パディング文字行
     Dim n As Long
     n = 12
     'バイト数行
     Dim h As Long
     h = 9
     '最終列数
     Dim lastcolumn As Long
     lastcolumn = ws.Cells(5, 3).End(xlToRight).Column
     
     Dim meisai As String
     meisai = ""
     
     'レコードループ
     Do While ws.Cells(i, j).Value <> ""
        'レコード開始列
        Dim k As Long

        For k = j To lastcolumn
            '固定値の場合
            If switch = 1 Then
                meisai = meisai & ファイル作成_明細固定(i, k, m, n, h)
            '可変値の場合
            ElseIf switch = 2 Then
                If k = lastcolumn Then
                    meisai = meisai & ws.Cells(i, k).Value
                Else
                    meisai = meisai & ws.Cells(i, k).Value & vbTab
                End If
                
            End If
        Next k
        meisai = meisai & line
        i = i + 1
    Loop
    ファイル作成_明細出力 = meisai
    
End Function

'i レコード開始行
'j レコード開始列
Function レコード件数取得(ByVal i As Long, ByVal j As Long) As Long
    '最終行数
    Dim lastrow As Long
    lastrow = ws.Cells(i, j).End(xlDown).Row
    If ws.Cells(i, j).Value = "" Then
        レコード件数取得 = 0
    Else
        レコード件数取得 = lastrow - (i - 1)
    End If
End Function

'i 処理日行
'j 処理日列
'm 何桁の日付出力
Function 日付(ByVal i As Long, ByVal j As Long, ByVal k As Long) As String
    If ws.Cells(i, j).Value = "" Then
        日付 = Format(Now, Right("YYYYMMDD", k))
    Else
        日付 = Right(ws.Cells(i, j).Value, k)
    End If
End Function

'file 作成ファイル名
Sub Ctlファイル作成(ByVal file As String)
    Dim datFileCtl As String
    datFileCtl = file & ".ctl"
    Open datFileCtl For Output As #1
    Close #1
End Sub


