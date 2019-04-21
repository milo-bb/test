--日時
\set :dtsys
--出力ファイル名
\set file count_ :dtsys .tsv

\pset format unaligned
\pset tuples_only on
\pset fieldsep '\t'

\o :file

select count(*) from test;


\o

