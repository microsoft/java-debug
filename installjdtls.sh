dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)

rm -rf jdtls/
rm -rf jdt-language-server-latest.tar.gz
wget https://vscjavaci.cloudapp.net/job/yaohai_jdtls_gzsnapshot/lastSuccessfulBuild/Azure/processDownloadRequest/yaohai/jdt-language-server-latest.tar.gz
mkdir jdtls
tar -xzf jdt-language-server-latest.tar.gz -C jdtls/
cd jdtls
jdtlscorejar=`find . -name 'org.eclipse.jdt.ls.core_*.jar'`
mvn install:install-file -Dfile=$jdtlscorejar -DgroupId=org.eclipse.jdt.ls -DartifactId=org.eclipse.jdt.ls.core -Dversion=0.4.0 -Dpackaging=jar
