#!/bin/bash
#Usage: ./pushToBintray.sh username apikey repo package version
BINTRAY_USER=$1
BINTRAY_API_KEY=$2
BINTRAY_REPO=$3
PCK_NAME=$4
PCK_VERSION=$5

SCRIPT=$(readlink -f "$0")
SCRIPTPATH=$(dirname "$SCRIPT")

function main() {
    cd $SCRIPTPATH/target/repository

    METADATA=./*
    PLUGINDIR=plugins/*

    echo "Processing p2 metadata file..."
    for f in $METADATA;
        do
            if [ ! -d $f ]; then
                echo "Pushing metadata file $f ..."
                filename=$(basename "$f")
                curl -X PUT -T $f -u ${BINTRAY_USER}:${BINTRAY_API_KEY} https://api.bintray.com/content/${BINTRAY_USER}/${BINTRAY_REPO}/$f;publish=0
                curl -X PUT -T $f -u ${BINTRAY_USER}:${BINTRAY_API_KEY} https://api.bintray.com/content/${BINTRAY_USER}/${BINTRAY_REPO}/${PCK_NAME}/${PCK_VERSION}/$filename;publish=0
                echo ""
            fi
        done

    echo "Processing plugins file..."
    for f in $PLUGINDIR;
        do
            echo "Pushing plugin file $f ..."
            curl -X PUT -T $f -u ${BINTRAY_USER}:${BINTRAY_API_KEY} https://api.bintray.com/content/${BINTRAY_USER}/${BINTRAY_REPO}/${PCK_NAME}/${PCK_VERSION}/$f;publish=0;override=1
            curl -X PUT -T $f -u ${BINTRAY_USER}:${BINTRAY_API_KEY} https://api.bintray.com/content/${BINTRAY_USER}/${BINTRAY_REPO}/${PCK_NAME}/${PCK_VERSION}/${PCK_NAME}/${PCK_VERSION}/$f;publish=0
            echo ""
        done

    echo "Publishing the new version"
    curl -X POST -u ${BINTRAY_USER}:${BINTRAY_API_KEY} https://api.bintray.com/content/${BINTRAY_USER}/${BINTRAY_REPO}/publish -d "{ \"discard\": \"false\" }"
    curl -X POST -u ${BINTRAY_USER}:${BINTRAY_API_KEY} https://api.bintray.com/content/${BINTRAY_USER}/${BINTRAY_REPO}/${PCK_NAME}/${PCK_VERSION}/publish -d "{ \"discard\": \"false\" }"
}

main "$@"