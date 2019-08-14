/**
 * Promote a repo to get it picked up by Maven Central.
 * 
 * Required binaries:
 * - curl
 * 
 * Required Environment Variables:
 * - NEXUS_OSSRHUSER: username.
 * - NEXUS_OSSRHPASS: password.
 * - NEXUS_STAGINGPROFILEID: identifier of the repo to promote.
 * - NEXUS_STAGINGREPOID: id of staging repo with artifacts to promote.
 */

const childProcess = require('child_process');

const configs = {
    nexus_ossrhuser: process.env.NEXUS_OSSRHUSER,
    nexus_ossrhpass: process.env.NEXUS_OSSRHPASS,
    nexus_stagingProfileId: process.env.NEXUS_STAGINGPROFILEID,
    stagingRepoId: process.env.NEXUS_STAGINGREPOID,
    groupId: "com.microsoft.java",
    projectName: "java-debug",
    moduleNames: [
        "java-debug-parent",
        "com.microsoft.java.debug.core",
        "com.microsoft.java.debug.plugin"
    ]
};

promoteStaging(configs);

function promoteStaging(configs) {
    let message = "";
    console.log("\n========Nexus: Promote=======");
    try {
        console.log(`Starting to promote staging repository ${configs.stagingRepoId} ...`);
        console.log(`curl -i -X POST -d "<promoteRequest><data><stagedRepositoryId>${configs.stagingRepoId}</stagedRepositoryId></data></promoteRequest>" -H "Content-Type: application/xml" -u **:** -k https://oss.sonatype.org/service/local/staging/profiles/${configs.nexus_stagingProfileId}/promote`);
        message = childProcess.execSync(`curl -i -X POST -d "<promoteRequest><data><stagedRepositoryId>${configs.stagingRepoId}</stagedRepositoryId></data></promoteRequest>" -H "Content-Type: application/xml" -u ${configs.nexus_ossrhuser}:${configs.nexus_ossrhpass} -k https://oss.sonatype.org/service/local/staging/profiles/${configs.nexus_stagingProfileId}/promote`);
        message = message.toString();
        console.log(message);
        const match = /HTTP\/2 20[01]/.exec(message);
        if (match == null || match.length <= 1) {
            console.error("\n\n[Failure] Promoting staging repository failed.");
            console.error(message);
            process.exit(1);
        }
    } catch (ex) {
        console.error("\n\n[Failure] Promoting staging repository failed.");
        console.error(!message ? ex : message.toString());
        process.exit(1);
    }
    console.log("\n\n[Success] Nexus: Promote completion.");
    console.log("Below is the public repository url, you could manually validate it.");
    console.log(`https://oss.sonatype.org/content/groups/public/${configs.groupId.replace(/\./g, "/")}`);
    console.log("\n\n");
}
