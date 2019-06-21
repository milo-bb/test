#!/bin/csh
setenv DB_CONNECT $1

#引数チェック
if ( $1 == '' ) then
    echo "引数１：データベース名を入力してください。"
    exit 1
endif

if ( $2 == '' ) then
    echo "引数２：SQLファイル名を入力してください。"
    exit 1
endif

if ( $5 == '' ) then
    echo "引数５：インタフェースIDを入力してください。"
    exit 1
endif

if ( $6 == '' ) then
    echo "引数６：禁則文字フラグを入力してください。"
    echo "禁則文字フラグ（0:禁則文字対応なし、1:禁則文字対応）"
    exit 1
endif

#SQLファイル
setenv SQL_FILE "/var/tmp/sql/"$2
#出力ファイルパス
setenv OUTPUT_FILE_PATH "/var/tmp/"
#バックアップファイルパス
setenv BACKUP_FILE_PATH "/var/tmp/backup/"
#日付FROM
setenv DATE_FROM $3
#日付TO
setenv DATE_TO $4
#インタフェースID
setenv INTERFACE_ID $5
#禁則文字フラグ
setenv KINSOKU_MOJI_FLG $6
#SQLファイル名カット
setenv SQL_FILE_NAME `echo $2 | cut -c 1-10`
#システム日時
setenv SYSTEM_TIME `date "+%Y%m%d%H%M%S"`
#移行データ作成日時
setenv IKO_TIME "`echo ${SYSTEM_TIME} | cut -c 0-4`-`echo ${SYSTEM_TIME} | cut -c 5-6`-`echo ${SYSTEM_TIME} | cut -c 7-8` `echo ${SYSTEM_TIME} | cut -c 9-10`:`echo ${SYSTEM_TIME} | cut -c 11-12`:`echo ${SYSTEM_TIME} | cut -c 13-14`"
#出力ファイル名
setenv OUTPUT_FILE_NAME "${INTERFACE_ID}_${SYSTEM_TIME}"

echo ${INTERFACE_ID} "ファイル出力開始"
echo "日付FROM："${DATE_FROM} "日付TO："${DATE_TO}

echo ""
echo "ファイル出力"
if ( ${DATE_FROM} == '' && ${DATE_TO} == '' ) then
    if ( ${KINSOKU_MOJI_FLG} == 0 ) then
        psql -h ${PGHOST} -U ${PGUSER} -d ${PGDATABASE} -p ${PGPORT} -t -c "\copy (`cat ${SQL_FILE} | sed -e s/IKOXXXXXXXX/"${IKO_TIME}"/g`) to ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv WITH DELIMITER AS E'\t'"
        if ( ${status} != 0 ) then
            echo "テーブル情報ファイル出力エラー"
            exit 1
        endif
    elseif ( ${KINSOKU_MOJI_FLG} == 1 ) then
        psql -h ${PGHOST} -U ${PGUSER} -d ${PGDATABASE} -p ${PGPORT} -q  -A -P fieldsep='	' -t  -c "`cat ${SQL_FILE} | sed -e s/IKOXXXXXXXX/"${IKO_TIME}"/g`"  > ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv
        if ( ${status} != 0 ) then
            echo "テーブル情報ファイル出力エラー"
            exit 1
        endif
    endif
endif

if ( ${DATE_FROM} != '' && ${DATE_TO} != '' ) then
    if ( ${KINSOKU_MOJI_FLG} == 0 ) then
        psql -h ${PGHOST} -U ${PGUSER} -d ${PGDATABASE} -p ${PGPORT} -t -c "\copy (`cat ${SQL_FILE} | sed -e s/FROMXXXXXXXX/${DATE_FROM}/g -e s/TOXXXXXXXX/${DATE_TO}/g -e s/IKOXXXXXXXX/"${IKO_TIME}"/g`) to ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv WITH DELIMITER AS E'\t'"
        if ( ${status} != 0 ) then
            echo "テーブル情報ファイル出力エラー"
            exit 1
        endif
    elseif ( ${KINSOKU_MOJI_FLG} == 1 ) then
        psql -h ${PGHOST} -U ${PGUSER} -d ${PGDATABASE} -p ${PGPORT} -q  -A -P fieldsep='	' -t  -c "`cat ${SQL_FILE} | sed -e s/FROMXXXXXXXX/${DATE_FROM}/g -e s/TOXXXXXXXX/${DATE_TO}/g -e s/IKOXXXXXXXX/"${IKO_TIME}"/g`"  > ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv
        if ( ${status} != 0 ) then
            echo "テーブル情報ファイル出力エラー"
            exit 1
        endif
    endif
endif

if ( ${DATE_FROM} != '' && ${DATE_TO} == '' ) then
    if ( ${KINSOKU_MOJI_FLG} == 0 ) then
        psql -h ${PGHOST} -U ${PGUSER} -d ${PGDATABASE} -p ${PGPORT} -t -c "\copy (`cat ${SQL_FILE} | sed -e s/FROMXXXXXXXX/${DATE_FROM}/g -e s/IKOXXXXXXXX/"${IKO_TIME}"/g`) to ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv WITH DELIMITER AS E'\t'"
        if ( ${status} != 0 ) then
            echo "テーブル情報ファイル出力エラー"
            exit 1
        endif
    elseif ( ${KINSOKU_MOJI_FLG} == 1 ) then
        psql -h ${PGHOST} -U ${PGUSER} -d ${PGDATABASE} -p ${PGPORT} -q  -A -P fieldsep='	' -t  -c "`cat ${SQL_FILE} | sed -e s/FROMXXXXXXXX/${DATE_FROM}/g -e s/IKOXXXXXXXX/"${IKO_TIME}"/g`"  > ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv
        if ( ${status} != 0 ) then
            echo "テーブル情報ファイル出力エラー"
            exit 1
        endif
    endif
endif

echo ""
echo "ファイル件数"
/usr/bin/wc -l ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv
if ( ${status} != 0 ) then
    echo "ファイル件数取得エラー"
    exit 1
endif

echo ""
echo "ファイル情報"
ls -l ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv
if ( ${status} != 0 ) then
    echo "ファイル情報取得エラー"
    exit 1
endif

echo ""
echo "ファイル圧縮"
/usr/bin/zip -rj ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME} ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv

if ( ${status} == 6) then
    echo "ファイル分割"
    set filecnt = `wc -l ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv | cut -d " " -f 1`
    set cnt = `expr ${filecnt} % 2`
    if (${cnt} == 0) then
        set cnt2 = `expr ${filecnt} / 2`
    else
        set cnt2 = `expr \( ${filecnt} + 1 \) / 2`
    endif
    split -l ${cnt2} -d ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}.tsv ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}_
    foreach file (`ls -1 ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}_[0-9][0-9]`)
        mv -f ${file} ${file}.tsv
       /usr/bin/zip -rj ${file} ${file}.tsv
       /usr/bin/wc -l ${file}.tsv
       ls -l ${file}.tsv
    end
endif

echo ""
echo "ファイルバックアップ"
if ( ! -d ${BACKUP_FILE_PATH} ) then
    mkdir ${BACKUP_FILE_PATH}
    if ( ${status} != 0 ) then
        echo "バックアップディレクトリ作成エラー"
        exit 1
    endif
endif

mv -f ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}*.zip ${BACKUP_FILE_PATH}
if ( ${status} != 0 ) then
    echo "バックアップ失敗"
    exit 1
endif

echo ""
echo "ファイル削除"
rm -f ${OUTPUT_FILE_PATH}${OUTPUT_FILE_NAME}*

echo ""
echo ${INTERFACE_ID} "ファイル出力完了"

exit 0
