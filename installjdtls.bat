cd %~dp0
bitsadmin.exe /transfer "downloadjdtls"/download /priority FOREGROUND https://vscjavaci.cloudapp.net/job/yaohai_jdtls_gzsnapshot/lastSuccessfulBuild/Azure/processDownloadRequest/yaohai/jdt-language-server-latest.tar.gz "%cd%/jdt-language-server-latest.tar.gz"
if exist jdtls (
    rm -rf jdtls/
)
mkdir jdtls
tar -xzf jdt-language-server-latest.tar.gz -C jdtls/
cd jdtls
for /R ./plugins %%F in (org.eclipse.jdt.ls.core_*.jar) do (
    echo %%F
    mvn install:install-file -Dfile="%%F" -DgroupId=org.eclipse.jdt.ls -DartifactId=org.eclipse.jdt.ls.core -Dversion=0.4.0 -Dpackaging=jar
)
