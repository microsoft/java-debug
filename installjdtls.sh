dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

wget https://vscjavaci.cloudapp.net/job/yaohai_jdtls_gzsnapshot/4/Azure/processDownloadRequest/yaohai/jdt-language-server-latest.tar.gz
rm -rf jdtls/
mkdir jdtls
tar -xzf jdt-language-server-latest.tar.gz -C jdtls/
jdtlscorejar=`find jdtls/ -name 'org.eclipse.jdt.ls.core_*.jar'`
mvn install:install-file -Dfile=$jdtlscorejar -DgroupId=org.eclipse.jdt.ls -DartifactId=org.eclipse.jdt.ls.core -Dversion=0.4.0 -Dpackaging=jar
