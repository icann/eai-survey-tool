#!/bin/bash

# --------------------------------------------------------------
# Script that exports the temporal database into a CSV file
# --------------------------------------------------------------
# sed 's/^.\{1,\}$/"&"/' inFile

AWS_DIR=/home/centos/.aws
SNOWSQL_DIR=/home/centos/.snowsql
SQL_FILE=/dev/shm/import.sql
TABLES=("progress" "record" "record_mx" "mx" "mx_ip" "ip")
DATE=$(date '+%Y%m%d')

# --- Delete old files ---
rm -rf csv/*.gz
rm -rf csv/*.csv

# --- Export, Compress and Upload ---
for table in ${TABLES[@]}; do
	echo -n "[$table] Exporting... "
	mysql -h 172.17.0.2 -u<user> -p<password> eai -e "select '$DATE', t.* from $table t into outfile '/csv/$table.csv' character set utf8 fields enclosed by '\"' terminated by ',' lines terminated by '\n'"
	echo -n "OK,   Compressing... "
	gzip -9 csv/$table.csv
	echo -n "OK,   Uploading... "
	docker run --rm -it -v $AWS_DIR:/root/.aws -v $PWD/csv:/aws amazon/aws-cli s3 cp $table.csv.gz s3://icann-eai-survey/$table.csv.gz --only-show-errors
	echo "OK"
done

# --- Display S3 Contents ---
echo ""
echo "Amazon s3://icann-eai-survey/ files:"
docker run --rm -it -v $AWS_DIR:/root/.aws amazon/aws-cli s3 ls s3://icann-eai-survey/


# --- Import data into Snowflake ---
echo ""
echo "Importing into Snowflake... "
echo "ALTER WAREHOUSE EAI_SURVEY_XS resume IF SUSPENDED;" > $SQL_FILE
for table in ${TABLES[@]}; do
	echo "DELETE FROM $table WHERE run = $DATE;" >> $SQL_FILE
	echo "COPY INTO $table FROM '@datastage/$table.csv.gz' FILE_FORMAT = (TYPE = CSV, FIELD_OPTIONALLY_ENCLOSED_BY = '\"',  ESCAPE = '\\\\');" >> $SQL_FILE
done
echo "ALTER WAREHOUSE EAI_SURVEY_XS SUSPEND;" >> $SQL_FILE
# cat $SQL_FILE
snowsql --config $SNOWSQL_DIR/config --filename $SQL_FILE
rm $SQL_FILE
