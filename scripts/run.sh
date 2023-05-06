#!/bin/bash
set -e

# --- Constants ---
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# --- Load utils ---
. $SCRIPT_DIR/utils.sh

# --- Load configuration ---
. $SCRIPT_DIR/scripts.cfg

# --- Validate Config ---
validate_config $SCRIPT_DIR/scripts.cfg

# --- Check for container manager ---
CM=$( find_container_manager )
if [ "$CM" == "" ]; then
	echo "** Please install podman or docker"
	exit 1
fi

display_title "Running the eai survey"
cm run --rm --name $EAI_NAME -ti --network host \
	-v $EAI_CONFIG_FILENAME:/home/jboss/$EAI_NAME-$EAI_VERSION/config.properties \
	-v $EAI_LOG_CONFIG_FILENAME:/home/jboss/$EAI_NAME-$EAI_VERSION/logging.properties \
	-v $EAI_LOGS_DIR:/logs \
	-v $EAI_MAXMIND_FILENAME:/home/jboss/$EAI_NAME-$EAI_VERSION/geoip/GeoIP2-Country.mmdb \
	-w /home/jboss/$EAI_NAME-$EAI_VERSION \
	-d $EAI_IMAGE $EAI_RUN_COMMAND
