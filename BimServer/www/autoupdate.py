# Updates the BIMserver

import http.client
import json
import pathlib
import os
import configparser
import urllib.request
import sys
import time
import zipfile
import shutil

def main():
    """The Main function"""

    currentVersionName = getCurrentVersion()
    print('current version: ' +  currentVersionName.toString())
    releases = fetchReleases()
    latestRelease = findLatestRelease(releases)
    latestVersionName = VersionName(latestRelease['name'])
    print('latest version: ' +  latestVersionName.toString())
    if(isLeftVersionHigherThanRight(latestVersionName, currentVersionName)):
        updateBimServer(latestRelease)
    else:
        print('BIMserver is already up to date!')

def getCurrentVersion():
    """lookup the current version of the BIMserver

    Returns:
        VersionName: the current Version of the BIMserver
    """

    parser = getVersionConfig()

    if parser.has_section('config') == False:
        return VersionName('0.0.-1')

    if parser.has_option('config', 'version') == False:
        return VersionName('0.0.-1')

    versionName = VersionName(parser.get('config', 'version'))
    return versionName

def getVersionConfig():
    """read the version config file

    Returns:
        ConfigParser: the parsed version config file
    """

    parser = configparser.RawConfigParser()
    currentPath = pathlib.Path(__file__).parent.resolve()
    versionConfigPath = str(currentPath) + '\\version.cfg'
    parser.read(versionConfigPath)
    return parser

def fetchReleases():
    """looks up all the releases on github

    Returns:
        List: all public releases as a list
    """

    headers = {
        "User-Agent":"Mozilla/5.0 (iPhone; CPU iPhone OS 5_0 like Mac OS X) AppleWebKit/534.46 (KHTML, like Gecko) Version/5.1 Mobile/9A334 Safari/7534.48.3",
        "Accept": "application/vnd.github.v3+json"
    }
    conn = http.client.HTTPSConnection("api.github.com")
    conn.request("GET", "/repos/WeltWeitBau/BIMserver/releases", None, headers)
    response = conn.getresponse()
    releases = json.loads(response.read())
    return releases

def findLatestRelease(releases):
    """looks up the latest release in a list of relases

    Args:
        releases (List): a list of releases

    Returns:
        Dictionary: the latest release as a dictionary
    """

    latestVersionName = VersionName('0.0.-1')
    latestRelease = None

    for release in releases:
        currentVersionName = VersionName(release['name'])

        if(isLeftVersionHigherThanRight(currentVersionName, latestVersionName)):
            latestVersionName = currentVersionName
            latestRelease = release

    return latestRelease

def isLeftVersionHigherThanRight(left, right):
    """determines wether left has a higher version number than right

    Args:
        left (VersionName): a version
        right (VersionName): another version

    Returns:
        Boolean: wether left has a higher version number than right
    """

    if left.major > right.major:
        return True
    if(left.major < right.major):
        return False

    # same major version
    if left.minor > right.minor:
        return True
    if(left.minor < right.minor):
        return False

    # same major and minor version
    return left.patch > right.patch

def updateBimServer(newVersion):
    """downloads the new patch and replaces the updated files

    Args:
        newVersion (Dictionary): the new Version as a dictionary
    """

    localFilePath = downloadPatch(newVersion)
    print(localFilePath)
    replaceFiles(localFilePath)
    updateVersionName(newVersion)

def downloadPatch(newVersion):
    """downloads the patch as a .zip file (actually a .war file)

    Args:
        newVersion (Dictionary): the new Version to download as a dictionary

    Returns:
        Path: the path to the downloaded patch
    """

    downloadUrl = getDownloadUrl(newVersion)
    print('download patch: ' +  downloadUrl)

    currentPath = pathlib.Path(__file__).parent.resolve()
    versionName = newVersion['name']

    localFilePath = pathlib.Path(str(currentPath) + '\\patch_' + versionName + '-138.zip')
    if localFilePath.is_file():
        return localFilePath

    urllib.request.urlretrieve(downloadUrl, str(localFilePath), reporthook)
    print('download done')
    return localFilePath

def getDownloadUrl(newVersion):
    """determine the download URL for this Version

    Args:
        newVersion (Dictionary): the new Version to download as a dictionary

    Returns:
        string: the download URL
    """

    assets = newVersion['assets']
    for asset in assets:
        downloadUrl = asset['browser_download_url']
        if downloadUrl.endswith('-138.zip'):
            return downloadUrl

def reporthook(count, block_size, total_size):
    """Updates the download progress"""
    global start_time
    start_time = 0

    if count == 0:
        start_time = time.time()
        return
    duration = time.time() - start_time
    progress_size = int(count * block_size)
    speed = int(progress_size / (1024 * duration))
    percent = int(count * block_size * 100 / total_size)
    sys.stdout.write("\r...%d%%, %d MB, %d KB/s, %d seconds passed" %
                    (percent, progress_size / (1024 * 1024), speed, duration))
    sys.stdout.flush()

def replaceFiles(patchFilePath):
    """Replace all files from the downloaded zip file. Skips content.xml and web.xml

    Args:
        patchFilePath (Path): the path to the downloaded zip file
    """

    currentPath = pathlib.Path(__file__).parent
    tempPath = os.path.join(currentPath, 'tempPatch')
    tempdir = str(tempPath)

    os.mkdir(tempdir)

    print('extract patch to: ' + tempdir)
    with zipfile.ZipFile(patchFilePath, 'r') as zip_ref:
        zip_ref.extractall(tempdir)

    for filename in os.listdir(tempdir):
        replaceFile(filename, tempdir)

    try:
        shutil.rmtree(tempdir)
    except OSError as e:
        print("Error: %s - %s." % (e.filename, e.strerror))

def replaceFile(fileName, tempDirRootName):
    """Replace a file from the unzipped patch. Skip content.xml and web.xml

    Args:
        fileName (string): the current files relative name (e.g. WEB-INF/lib/foorbar.jar)
        tempDirRootName (string): the unzipped patch files path
    """

    tempFileName = tempDirRootName + '/' + fileName
    if(os.path.isfile(tempFileName) == False):
        replaceDir(fileName, tempDirRootName)
        return

    if(fileName == 'WEB-INF/context.xml'):
        return
    if(fileName == 'WEB-INF/web.xml'):
        return

    print('replace file: ' + fileName)
    contextPath = pathlib.Path(__file__).parent.resolve()
    path = os.replace(tempFileName, str(contextPath) + '/' + fileName)

def replaceDir(dirName, tempDirRootName):
    """Replace all files from this directory

    Args:
        dirName (string): the current directories relative name (e.g. WEB-INF/lib)
        tempDirRootName (string): the unzipped patch files path
    """

    tempDirName = tempDirRootName + '/' + dirName
    if(os.path.isdir(tempDirName) == False):
        return

    print('replace directory: ' + dirName)

    contextPath = pathlib.Path(__file__).parent.resolve()

    if(dirName == 'WEB-INF/lib'):
        try:
            shutil.rmtree(str(contextPath) + '/' + dirName)
        except OSError as e:
            print("Error: %s - %s." % (e.filename, e.strerror))

    try:
        os.mkdir(str(contextPath) + '/' + dirName)
    except OSError as e:
        print(dirName + ' exists')

    for filename in os.listdir(tempDirName):
        replaceFile(dirName + '/' + filename, tempDirRootName)

def updateVersionName(versionName):
    """Updates the version number in version.cfg

    Args:
        versionName (VersionName): the new version name
    """
    currentPath = pathlib.Path(__file__).parent.resolve()
    versionConfigPath = str(currentPath) + '\\version.cfg'
    parser = getVersionConfig()

    if parser.has_section('config') == False:
        parser.add_section('config')

    parser.set('config', 'version', versionName['name'])
    with open(versionConfigPath, 'w') as configfile:
        print('update version number: ' + versionName['name'])
        parser.write(configfile)

class VersionName:
    major = 0
    minor = 0
    patch = 0

    def __init__(self, versionAsString):
        versionAsString.replace("", "-138")
        versionAsArray = versionAsString.replace('v', '').split('.')
        self.major = int(versionAsArray[0])
        self.minor = int(versionAsArray[1])
        self.patch = int(versionAsArray[2])

    def toString(self):
        return 'v' + str(self.major) + '.' + str(self.minor) + '.' + str(self.patch)

if __name__ == "__main__":
    main()