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

# --- Check dependencies ---
display_title "Check dependencies"
check_for_dependencies "buildah" "tar" "gzip"

# --- Check for root user in namespace ---
display_title "Check namespace"
grep -qlE '^\s+0\s+0' /proc/self/uid_map \
	&& echo "** Please run under buildah unshare"  \
	&& exit

# --- Downloading images ---
display_title "Downlading images"
buildah pull $BUILDAH_BUILDER_IMAGE
buildah pull $BUILDAH_RUN_IMAGE

# --- Building the application ---
display_title "Creating builder container"
c1=$(buildah from $BUILDAH_BUILDER_IMAGE)
m1=$(buildah mount $c1)
buildah copy $c1 . .
buildah run $c1 mvn package
buildah run $c1 mvn assembly:single

# --- Creating the running image  ---
display_title "Creating the running image"
c2=$(buildah from $BUILDAH_RUN_IMAGE)
m2=$(buildah mount $c2)
tar -xvf $m1/home/jboss/target/$EAI_NAME-$EAI_VERSION-distribution.tar.gz --exclude *.sh -C $m2/home/jboss/
buildah config --workingdir /home/jboss/$EAI_NAME-$EAI_VERSION $c2

# --- Umount the containers ---
display_title "Umount the containers"
buildah umount $c1
buildah umount $c2

# --- Commit Image ---
display_title "Commit image"
buildah commit --format docker $c2 $EAI_IMAGE

# --- Delete containers ---
display_title "Delete containers"
buildah rm $c1
buildah rm $c2
