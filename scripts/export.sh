#!/bin/bash
set -e

# --------------------------------------------------------------
# Script that exports the temporal database into a CSV file
# --------------------------------------------------------------
# sed 's/^.\{1,\}$/"&"/' inFile

# --- Constants ---
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DATE=$(date '+%Y%m%d')

# --- Load utils ---
. $SCRIPT_DIR/utils.sh

# --- Load configuration ---
. $SCRIPT_DIR/scripts.cfg

# --- Validate config ---
validate_config $SCRIPT_DIR/scripts.cfg

# --- Check for container manager ---
CM=$( find_container_manager )
if [ "$CM" == "" ]; then
	echo "** Please install podman or docker"
	exit 1
fi

# --- Check dependencies ---
display_title "Check dependencies"
check_for_dependencies "$CM" "gzip"

# --- Delete old files ---
rm -rf $CSV_DIR/*.gz
rm -rf $CSV_DIR/*.csv

# --- Export, Compress and Upload ---
for table in ${TABLES[@]}; do
	echo -n "[$table] Exporting... "
	cm run --rm --name exporter --network host -ti $DB_IMAGE \
		mysql -h $DB_BIND_ADDR -P $DB_BIND_PORT -u root eai -e "select '$DATE', t.* from $table t into outfile '/csv/$table.csv' character set utf8 fields enclosed by '\"' terminated by ',' lines terminated by '\n'"
	echo -n "OK,   Compressing... "
	gzip -9 $CSV_DIR/$table.csv
	echo -n "OK,   Uploading... "
	cm run --rm -it -v $AWS_DIR:/root/.aws -v $CSV_DIR:/aws $AWS_IMAGE s3 cp $table.csv.gz $S3_URL/$table.csv.gz --only-show-errors
	echo "OK"
done

# --- Display S3 Contents ---
echo ""
echo "Amazon $S3_URL files:"
cm run --rm -it -v $AWS_DIR:/root/.aws $AWS_IMAGE s3 ls $S3_URL

# --- Import data into Snowflake ---
echo ""
echo "Importing into Snowflake... "
echo "ALTER WAREHOUSE EAI_SURVEY_XS resume IF SUSPENDED;" > $SQL_FILENAME
for table in ${TABLES[@]}; do
	echo "DELETE FROM $table WHERE run = $DATE;" >> $SQL_FILENAME
	echo "COPY INTO $table FROM '@datastage/$table.csv.gz' FILE_FORMAT = (TYPE = CSV, FIELD_OPTIONALLY_ENCLOSED_BY = '\"',  ESCAPE = '\\\\');" >> $SQL_FILENAME
done
echo "ALTER WAREHOUSE EAI_SURVEY_XS SUSPEND;" >> $SQL_FILENAME
snowsql --config $SNOWSQL_DIR/config --filename $SQL_FILENAME
rm $SQL_FILENAME
