# --- Constants ---
COLOR_BLUE='\033[1;34m'
COLOR_GREEN='\033[0;32m'
COLOR_RED='\033[0;31m'
COLOR_NONE='\033[0m'

# --- Validate configuration ---
function validate_config() {
	for conf in $(cat $1 | grep -P "^# [A-Z]+_[A-Z]+" | awk '{print $2}'); do
		eval "value=\"\${$conf}\""
		if [ "$value" == "" ]; then
			echo "** Missing configuration parameter: $conf"
			exit 1
		elif [[ "$conf" == *_DIR ]] || [[ "$conf" == *_FILENAME ]]; then
			if [[ "$value" != /* ]]; then
				echo "** Please use a full path for the parameter: $conf. Remember that you can use env variables like '\$PWD' and '\$HOME'"
				exit 1
			fi
		fi
	done
}

# --- Display a header ---
function display_title() {
	printf "\n\n$COLOR_BLUE=== $1 ===$COLOR_NONE\n"
}

# --- Check for dependencies ---
function check_for_dependencies() {
	list=("$@")
	ALL_FOUND="Y"
	for cmd in "${list[@]}"; do
		echo -n "[ "
		if ! command -v $cmd &> /dev/null; then
			printf $COLOR_RED"NOT FOUND"$COLOR_NONE
			ALL_FOUND="N"
		else
			printf $COLOR_GREEN"  FOUND  "$COLOR_NONE
		fi
		echo " ] $cmd"
	done;
	if [ "$ALL_FOUND" == "N" ]; then
		printf "\n** Please install missing dependencies\n"
		exit 1
	fi
}

# --- Find container manager ---
function find_container_manager() {
	if command -v podman &> /dev/null; then
		echo "podman"
	elif command -v docker &> /dev/null; then
		echo "docker"
	else
		echo ""
	fi
}

# --- Exeute container manager ---
function cm() {
	if command -v podman &> /dev/null; then
		podman "$@"
	elif command -v docker &> /dev/null; then
		docker "$@"
	else
		echo "Podman or docker not found!"
		exit 1
	fi
}
