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

# --- MYSQL ---
function mysql() {
	cm run --rm --name reporter --network host -ti $DB_IMAGE \
	mysql -h $DB_BIND_ADDR -P $DB_BIND_PORT -u$DB_USERNAME -p$DB_PASSWORD eai "$@"
}

display_title "Report"
mysql -e "select name, timestamp from progress order by id"

echo ""
echo -n "Number of SLD: "
mysql -Nse "select format(count(*),0) from record"
mysql -e "select case when status = 2 then 'ServFail' when status = 3 then 'NxDomain' when status = 5 then 'Refused' when status = 'S' then '0K' when status = 'X' then 'No MX'end as result, count from (select status, format(count(*),0) as count from record group by 1) as t order by result"

echo ""
echo -n "Number of MX: "
mysql -Nse "select format(count(*),0) from mx"

echo ""
echo -n "Number of IP: "
mysql -Nse "select format(count(*),0) from ip"
echo -n "- Server responded: "
mysql -Nse "select format(count(*),0) from ip where header is not null"
echo -n "- Server timeout: "
mysql -Nse "select format(count(*),0) from ip where header is null and status = 'T'"
echo ""
echo -n "- Test allowed: "
mysql -Nse "select format(count(*),0) from ip where header like '220%'"
echo -n "- Test denied: "
mysql -Nse "select format(count(*),0) from ip where header like '5%'"
echo ""
echo -n "- EHLO test passed: "
mysql -Nse "select format(count(*),0) from ip where ehlo_success='Y'"
echo -n "- ASCII test passed: "
mysql -Nse "select format(count(*),0) from ip where ascii_success='Y'"
echo -n "- UTF-8 test passed: "
mysql -Nse "select format(count(*),0) from ip where idn_success='Y'"
