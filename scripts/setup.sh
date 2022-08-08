#!/bin/bash

# --------------------------------------------------------------
# Script that setup the temporal database and imports the zones.
# --------------------------------------------------------------

DB_DIR="data"
DB_NAME="maria"
DB_MEM="20G"
CSV_DIR="csv"
ZONES_DATA="/data/zones"
ZONES_DIR="zones"

# --- Ask the user for configuration parameters ---
if [ -d $PWD/$ZONES_DIR ]; then
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
if [ -d $PWD/$DB_DIR ]; then
	read -p "Database found, do you want to delete it? [y/N]: " delete
	delete=${delete:-N}
	if [ $delete == "y" ]; then
		if [ "$(docker ps -a | grep $DB_NAME)" ]; then
			docker stop $DB_NAME
		fi
		sudo rm -rf $DB_DIR
		mkdir $DB_DIR
		load='Y'
	elif [ $delete != "N" ]; then
		echo "Invalid option: $delete"
		exit 1
	fi
else
	mkdir $DB_DIR
	load='Y'
fi

if [ -d $PWD/$CSV_DIR ]; then
	read -p "CSV directory found, do you want to delete if? [y/N]: " delete
	delete=${delete:-N}
	if [ $delete == "y" ]; then
		sudo rm -rf $CSV_DIR
		mkdir $CSV_DIR
		sudo chown systemd-coredump.centos $CSV_DIR
		sudo chmod g+x $CSV_DIR
	elif [ $delete != "N" ]; then
		echo "Invalid option: $delete"
		exit 1
	fi
else
	mkdir $CSV_DIR
	sudo chown systemd-coredump.centos $CSV_DIR
	sudo chmod g+x $CSV_DIR
fi


# --- Decompress the zones files and create the SQL import file ---
if [ ! -d $PWD/$ZONES_DIR ]; then
	mkdir $ZONES_DIR
	base=$(ls -rd /data/zones/* | head -n 1)
	for zone in $(ls $base); do
		echo "decompressing... $base/$zone/$zone.zone.gz"
		cat $base/$zone/$zone.zone.gz | gzip -dk | grep -P '\tin\tns\t' |  awk '{print $1}' | uniq | sed "s/$/	$zone.	N/" >> $ZONES_DIR/record.sql
	done
fi

# --- Create the temporal dataase ---
if [ ! "$(docker ps -a | grep $DB_NAME)" ]; then
	docker run --rm --name $DB_NAME \
	-e MYSQL_ROOT_PASSWORD=<password> \
	-e MYSQL_DATABASE=eai \
	-e MYSQL_USER=<user> \
	-e MYSQL_PASSWORD=<password> \
	-v $PWD/$DB_DIR:/var/lib/mysql \
	-v $PWD/sql/tables.sql:/docker-entrypoint-initdb.d/tables.sql \
	-v $PWD/csv:/csv \
	-d mariadb \
	--net-read-timeout=7200 \
	--net-write-timeout=7200 \
	--innodb-buffer-pool-size=$DB_MEM

	echo "Waiting for the database..."
	sleep 10
fi

# --- Imports the zones into the tempral database using the SQL import file ---
if [ $load == "Y" ] && [ -f $ZONES_DIR/record.sql ]; then
	echo "Loading zones..."
	docker run --rm --name importer -ti -v $PWD/$ZONES_DIR:/zones mariadb mysqlimport --verbose --ignore -h 172.17.0.2 -u<user> -p<password> --local eai /zones/record.sql
fi

echo "To run the survey: java -Xmx4g -jar eai-survey-1.1.0.jar"
