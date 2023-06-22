#!/bin/bash
set -e

# --------------------------------------------------------------
# Script that setup the temporal database and imports the zones.
# --------------------------------------------------------------

# --- Constants ---
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

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
check_for_dependencies "$CM" "awk" "cat" "grep" "gzip" "uniq" "sed"

# --- Ask the user for configuration parameters ---
if [ -d $ZONES_DIR ]; then
	read -p "Zones directory found, do you want to delete it? [y/N]: " delete
	delete=${delete:-N}
	if [ $delete == "y" ]; then
		rm -rf $ZONES_DIR
	elif [ $delete != "N" ]; then
		echo "Invalid option: $delete"
		exit 1
	fi
fi

load='N'
if [ -d $DB_DIR ]; then
	read -p "Database found, do you want to delete it? [y/N]: " delete
	delete=${delete:-N}
	if [ $delete == "y" ]; then
		if [ "$(cm ps -a | grep $DB_NAME)" ]; then
			cm stop $DB_NAME
		fi
		sudo rm -rf $DB_DIR
		mkdir -p $DB_DIR
		load='Y'
	elif [ $delete != "N" ]; then
		echo "Invalid option: $delete"
		exit 1
	fi
else
	mkdir $DB_DIR
	load='Y'
fi

if [ -d $CSV_DIR ]; then
	read -p "CSV directory found, do you want to delete it? [y/N]: " delete
	delete=${delete:-N}
	if [ $delete == "y" ]; then
		rm -rf $CSV_DIR
		mkdir -p $CSV_DIR
		chmod a+w $CSV_DIR
	elif [ $delete != "N" ]; then
		echo "Invalid option: $delete"
		exit 1
	fi
else
	mkdir $CSV_DIR
	chmod a+w $CSV_DIR
fi

# --- Decompress the zones files and create the SQL import file ---
if [ ! -d $ZONES_DIR ]; then
	display_title "Reading zone files"
	mkdir $ZONES_DIR
	base=$(ls -rd $ZONES_SRC_DIR/* | head -n 1)
	for zone in $(ls $base); do
		echo "decompressing... $base/$zone/$zone.zone.gz"
		cat $base/$zone/$zone.zone.gz | gzip -dk | grep -P '\t[iI][nN]\t[nN][sS]\t' |  awk '{print $1}' | uniq | sed "s/$/	$zone.	N/" >> $ZONES_DIR/record.sql
	done
fi

# --- Create the temporal dataase ---
if [ ! "$(cm ps -a | grep $DB_NAME)" ]; then
	display_title "Creating the temporal database"
	cm run --rm --name $DB_NAME \
		-e MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=yes \
		-e MARIADB_DATABASE=eai \
		-e MARIADB_USER=$DB_USERNAME \
		-e MARIADB_PASSWORD=$DB_PASSWORD \
		-v $DB_DIR:/var/lib/mysql \
		-v $DB_INIT_SCRIPT_FILENAME:/docker-entrypoint-initdb.d/tables.sql \
		-v $CSV_DIR:/csv \
		-p $DB_BIND_ADDR:$DB_BIND_PORT:3306 \
		-d $DB_IMAGE \
		--net-read-timeout=7200 \
		--net-write-timeout=7200 \
		--innodb-buffer-pool-size=$DB_MEM

	echo "Waiting for the database..."
	sleep 10
fi

# --- Imports the zones into the tempral database using the SQL import file ---
if [ $load == "Y" ] && [ -f $ZONES_DIR/record.sql ]; then
	display_title "Loading zones"
	cm run --rm --name importer --network host -ti -v $ZONES_DIR:/zones $DB_IMAGE \
		mariadb-import --verbose --ignore -h $DB_BIND_ADDR -P $DB_BIND_PORT \
		-u$DB_USERNAME -p$DB_PASSWORD --local eai /zones/record.sql
fi

echo "To run the survey: run the script run.sh"
