#!/bin/bash

# --------------------------------------------------------------
# Script that setup the temporal database and imports the zones.
# --------------------------------------------------------------

MYSQL="mysql -h 172.17.0.2 -u<user> -p<password> eai"

echo ""
$MYSQL -e "select name, timestamp from progress order by id"

echo ""
echo -n "Number of SLD: "
$MYSQL -Nse "select format(count(*),0) from record"
$MYSQL -e "select case when status = 2 then 'ServFail' when status = 3 then 'NxDomain' when status = 5 then 'Refused' when status = 'S' then '0K' when status = 'X' then 'No MX'end as result, count from (select status, format(count(*),0) as count from record group by 1) as t order by result"

echo ""
echo -n "Number of MX: "
$MYSQL -Nse "select format(count(*),0) from mx"

echo ""
echo -n "Number of IP: "
$MYSQL -Nse "select format(count(*),0) from ip"
echo -n "- Server responded: "
$MYSQL -Nse "select format(count(*),0) from ip where header is not null"
echo -n "- Server timeout: "
$MYSQL -Nse "select format(count(*),0) from ip where header is null and status = 'T'"
echo ""
echo -n "- Test allowed: "
$MYSQL -Nse "select format(count(*),0) from ip where header like '220%'"
echo -n "- Test denied: "
$MYSQL -Nse "select format(count(*),0) from ip where header like '5%'"
echo ""
echo -n "- EHLO test passed: "
$MYSQL -Nse "select format(count(*),0) from ip where ehlo_success='Y'"
echo -n "- ASCII test passed: "
$MYSQL -Nse "select format(count(*),0) from ip where ascii_success='Y'"
echo -n "- UTF-8 test passed: "
$MYSQL -Nse "select format(count(*),0) from ip where idn_success='Y'"
