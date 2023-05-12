/**
 * Usage:
 * node publishMaven.js -task [gpg][upload|promote]
 * 
 * gpg: Sign artifacts with GPG.
 * upload: Upload artifacts to a nexus staging repo.
 * promote: Promote a repo to get it picked up by Maven Central.
 */

const childProcess = require('child_process');
const fs = require('fs');
const path = require('path');

const artifactFolder = process.env.artifactFolder;
const configs = {
    nexus_ossrhuser: process.env.NEXUS_OSSRHUSER,
    nexus_ossrhpass: process.env.NEXUS_OSSRHPASS,
    nexus_stagingProfileId: process.env.NEXUS_STAGINGPROFILEID,
    gpgpass: process.env.GPGPASS,
    stagingRepoId: process.env.NEXUS_STAGINGREPOID,
    groupId: "com.microsoft.java",
    projectName: "java-debug",
    releaseVersion: process.env.releaseVersion,    
    moduleNames: [
        "java-debug-parent",
        "com.microsoft.java.debug.core",
        "com.microsoft.java.debug.plugin"
    ],
    artifactFolder: artifactFolder
};

main(configs, artifactFolder);

function main() {
    const argv = process.argv;
    const task = argv[argv.indexOf("-task") + 1];
    if (task === "gpg") {
        pgpSign(configs, artifactFolder);
    } else if (task === "upload") {
        uploadToStaging(configs, artifactFolder);
    } else if (task === "promote") {
        promoteToCentral(configs);
    } else {
        console.error("Task not specified.");
        console.log("Usage: node script.js -task [upload|promote]");
    }
}

/**
 * Task gpg: Sign artifacts with GPG.
 * 
 * Required binaries:
 * - gpg
 * 
 * Required Environment Variables:
 * - artifactFolder: folder containing *.jar/*.pom files.
 * - GPGPASS: passphrase of GPG key.
 */
function pgpSign(configs, artifactFolder) {
    const props = ["artifactFolder", "gpgpass" ];
    for (const prop of props) {
        if (!configs[prop]) {
            console.error(`${prop} is not set.`);
            process.exit(1);
        }
    }
    addChecksumsAndGpgSignature(configs, artifactFolder);
}

/**
 * Task upload: Upload artifacts to a nexus staging repo.
 * 
 * Required binaries:
 * - gpg
 * - curl
 * 
 * Required Environment Variables:
 * - artifactFolder: folder containing *.jar/*.pom files.
 * - releaseVersion: version of artifacts.
 * - NEXUS_OSSRHUSER: username.
 * - NEXUS_OSSRHPASS: password.
 * - NEXUS_STAGINGPROFILEID: identifier of the repo to promote.
 * - GPGPASS: passphrase of GPG key.
 */
function uploadToStaging(configs, artifactFolder) {
    checkPrerequisite(configs);
    addChecksumsAndGpgSignature(configs, artifactFolder);
    createStagingRepo(configs);
    deployToStagingRepo(configs, artifactFolder);
    closeStagingRepo(configs);
}

 /**
 * Task promote: Promote a repo to get it picked up by Maven Central.
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
function promoteToCentral(configs) {
    let message = "";
    console.log("\n========Nexus: Promote=======");
    try {
        console.log(`Starting to promote staging repository ${configs.stagingRepoId} ...`);
        console.log(`curl -i -X POST -d "<promoteRequest><data><stagedRepositoryId>${configs.stagingRepoId}</stagedRepositoryId></data></promoteRequest>" -H "Content-Type: application/xml" -u **:** -k https://oss.sonatype.org/service/local/staging/profiles/${configs.nexus_stagingProfileId}/promote`);
        message = childProcess.execSync(`curl -i -X POST -d "<promoteRequest><data><stagedRepositoryId>${configs.stagingRepoId}</stagedRepositoryId></data></promoteRequest>" -H "Content-Type: application/xml" -u ${configs.nexus_ossrhuser}:${configs.nexus_ossrhpass} -k https://oss.sonatype.org/service/local/staging/profiles/${configs.nexus_stagingProfileId}/promote`);
        message = message.toString();
        console.log(message);
    } catch (ex) {
        console.error("\n\n[Failure] Promoting staging repository failed.");
        console.error(!message ? ex : message.toString());
        process.exit(1);
    }
    const success = isReleased(configs);
    console.log("Below is the public repository url, you could manually validate it.");
    console.log(`https://oss.sonatype.org/content/groups/public/${configs.groupId.replace(/\./g, "/")}`);
    console.log("\n\n");
    if (success) {
        console.log("\n\n[Success] Nexus: Promote succeeded.");
    } else {
        console.error("\n\n[Failure] Nexus: Promote failed.");
        process.exit(1)
    }
}

function isReleased(configs) {
    let pollingCount = 0;
    const MAX_POLLINGS = 10;
    for (; pollingCount < MAX_POLLINGS; pollingCount++) {
        console.log(`\nPolling the release operation finished or not...`);
        console.log(`curl -X GET -H "Content-Type:application/xml" -u **:** -k https://oss.sonatype.org/service/local/staging/repository/${configs.stagingRepoId}`);
        message = childProcess.execSync(`curl -X GET -H "Content-Type:application/xml" -u ${configs.nexus_ossrhuser}:${configs.nexus_ossrhpass} -k https://oss.sonatype.org/service/local/staging/repository/${configs.stagingRepoId}`);
        const status = extractStatus(message.toString());
        console.log(status);
        if (status !== "closed") {
            return true;
        }
        // use system sleep command to pause the program.
        childProcess.execSync(`sleep 6s`);
    }
    return false;
}

function checkPrerequisite(configs) {
    const props = ["releaseVersion", "artifactFolder", "nexus_ossrhuser", "nexus_ossrhpass", "nexus_stagingProfileId", "gpgpass" ];
    for (const prop of props) {
        if (!configs[prop]) {
            console.error(`${prop} is not set.`);
            process.exit(1);
        }
    }
}

function addChecksumsAndGpgSignature(configs, artifactFolder) {
    console.log("\n=======Checksum and gpg sign=======");
    console.log("Starting to calculate checksum and gpg sign...");
    for (let moduleName of configs.moduleNames) {
        const modulePath = path.join(artifactFolder, moduleName);
        // remove old md5/sha1/asc files.
        fs.readdirSync(modulePath)
            .filter(name => name.endsWith(".md5") || name.endsWith(".sha1") || name.endsWith(".asc"))
            .forEach(name => fs.unlinkSync(path.join(modulePath, name)));

        const files = fs.readdirSync(modulePath);
        for (let file of files) {
            // calc md5.
            const md5 = childProcess.execSync(`md5sum "${path.join(modulePath, file)}"`);
            const md5Match = /([a-z0-9]{32})/.exec(md5.toString());
            fs.writeFileSync(path.join(modulePath, file + ".md5"), md5Match[0]);

            // calc sha1.
            const sha1 = childProcess.execSync(`sha1sum "${path.join(modulePath, file)}"`);
            const sha1Match = /([a-z0-9]{40})/.exec(sha1.toString());
            fs.writeFileSync(path.join(modulePath, file + ".sha1"), sha1Match[0]);

            // gpg sign.
            childProcess.execSync(`gpg --batch --pinentry-mode loopback --passphrase "${configs.gpgpass}" -ab "${path.join(modulePath, file)}"`)
        }
    }
    console.log("\n\n[Success] Checksum and gpg sign finished.");
    console.log("\n\n");
}

function createStagingRepo(configs) {
    let message = "";
    console.log("\n=======Nexus: Create staging repo=======");
    console.log("Starting to create staging repository...");
    try {
        console.log(`curl -X POST -d "<promoteRequest><data><description>${configs.projectName}-${configs.releaseVersion}</description></data></promoteRequest>" -H "Content-Type: application/xml" -u **:** -k https://oss.sonatype.org/service/local/staging/profiles/${configs.nexus_stagingProfileId}/start`);
        message = childProcess.execSync(`curl -X POST -d "<promoteRequest><data><description>${configs.projectName}-${configs.releaseVersion}</description></data></promoteRequest>" -H "Content-Type: application/xml" -u ${configs.nexus_ossrhuser}:${configs.nexus_ossrhpass} -k https://oss.sonatype.org/service/local/staging/profiles/${configs.nexus_stagingProfileId}/start`);
        message = message.toString();
        const match = /<stagedRepositoryId>([a-zA-Z0-9-_]+)<\/stagedRepositoryId>/.exec(message);
        if (match != null && match.length > 1) {
            configs.stagingRepoId = match[1];
        } else {
            console.error("\n[Failure] Creating staging repository failed.");
            console.error(message);
            process.exit(1);
        }
    } catch (ex) {
        console.error("\n[Failure] Creating staging repository failed.");
        console.error(!message ? ex : message.toString());
        process.exit(1);
    }
    console.log("\n\n[Success] Nexus: Creating staging repository completion.");
    console.log("staging repository id: " + configs.stagingRepoId);
    console.log("\n\n");
}

function deployToStagingRepo(configs, artifactFolder) {
    console.log("\n========Nexus: Deploy artifacts to staging repo=======");
    console.log("Starting to deploy artifacts to staging repository...");
    for (let moduleName of configs.moduleNames) {
        const modulePath = path.join(artifactFolder, moduleName);
        for (let file of fs.readdirSync(modulePath)) {
            const realPath = path.join(modulePath, file);
            const url = [
                "https://oss.sonatype.org/service/local/staging/deployByRepositoryId",
                configs.stagingRepoId,
                configs.groupId.replace(/\./g, "/"),
                moduleName,
                configs.releaseVersion,
                file
            ];
            console.log(`curl --upload-file "${realPath}" -u **:** -k ${url.join("/")}`);
            message = childProcess.execSync(`curl --upload-file "${realPath}" -u ${configs.nexus_ossrhuser}:${configs.nexus_ossrhpass} -k ${url.join("/")}`);
            message = message.toString();
            console.log(message);
            console.log("Succeeded.\n");
        }
    }
    console.log("\n\n[Success] Nexus: Deploying completion.");
    console.log("\n\n");
}

function closeStagingRepo(configs) {
    let message = "";
    let pollingCount = 0;
    const MAX_POLLINGS = 10;
    console.log("\n========Nexus: Verify and Close staging repo=======");
    try {
        console.log(`Starting to close staging repository ${configs.stagingRepoId} ...`);
        console.log(`curl -X POST -d "<promoteRequest><data><stagedRepositoryId>${configs.stagingRepoId}</stagedRepositoryId></data></promoteRequest>" -H "Content-Type: application/xml" -u **:** -k https://oss.sonatype.org/service/local/staging/profiles/${configs.nexus_stagingProfileId}/finish`);
        message = childProcess.execSync(`curl -X POST -d "<promoteRequest><data><stagedRepositoryId>${configs.stagingRepoId}</stagedRepositoryId></data></promoteRequest>" -H "Content-Type: application/xml" -u ${configs.nexus_ossrhuser}:${configs.nexus_ossrhpass} -k https://oss.sonatype.org/service/local/staging/profiles/${configs.nexus_stagingProfileId}/finish`);
        message = message.toString();

        for (; pollingCount < MAX_POLLINGS; pollingCount++) {
            console.log(`\nPolling the close operation finished or not...`);
            console.log(`curl -X GET -H "Content-Type:application/xml" -u **:** -k https://oss.sonatype.org/service/local/staging/repository/${configs.stagingRepoId}`);
            message = childProcess.execSync(`curl -X GET -H "Content-Type:application/xml" -u ${configs.nexus_ossrhuser}:${configs.nexus_ossrhpass} -k https://oss.sonatype.org/service/local/staging/repository/${configs.stagingRepoId}`);
            // console.log(message.toString());
            if (extractStatus(message.toString()) === "closed") {
                break;
            }
            // use system sleep command to pause the program.
            childProcess.execSync(`sleep 6s`);
        }

        if (pollingCount >= MAX_POLLINGS) {
            console.log("\nQuerying the close operation result...");
            message = childProcess.execSync(`curl -X GET -H "Content-Type:application/xml" -u ${configs.nexus_ossrhuser}:${configs.nexus_ossrhpass} -k https://oss.sonatype.org/service/local/staging/repository/${configs.stagingRepoId}/activity`);
            // console.log(message.toString());
            const errors = extractErrorMessage(message.toString());
            console.error(`\n\n[Failure] Closing staging repository failed.`);
            console.error(`See failure messages:`);
            console.error(errors.join("\n\n"));
            process.exit(1);
        }
    } catch (ex) {
        console.error("\n\n[Failure] Closing staging repository failed.");
        console.error(!message ? ex : message.toString());
        process.exit(1);
    }
    fs.writeFileSync(".stagingRepoId", configs.stagingRepoId);
    console.log("\n\n[Success] Nexus: Staging completion.");
    console.log("Below is the staging repository url, you could use it to test deployment.");
    console.log(`https://oss.sonatype.org/content/repositories/${configs.stagingRepoId}`);
    console.log("\n\n");
}

function extractStatus(message) {
    const group = /<type>([a-zA-Z0-9-_\.]+)<\/type>/.exec(message);
    return group[1];
}

function extractErrorMessage(message) {
    const errors = [];
    const group = message.match(/<name>failureMessage<\/name>[\r?\n ]+<value>(.*)<\/value>/g);
    for (let error of group) {
        errors.push(error.match(/<value>(.*)<\/value>/)[1])
    }
    return errors;
}
