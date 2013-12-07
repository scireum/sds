/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */
package main

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"hash/crc32"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"path/filepath"
	"strings"
    "time"
    "strconv"
)

func md5File(path string) (string, error) {
	hash := md5.New()
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()

	buf := make([]byte, 8192)
	for {
		// read a chunk
		n, err := f.Read(buf)
		if err != nil && err != io.EOF {
			return "", err
		}
		if n == 0 {
			break
		}

		// write a chunk
		if _, err := hash.Write(buf[:n]); err != nil {
			return "", err
		}
	}

	return hex.EncodeToString(hash.Sum(nil)), nil
}

func crcFile(path string) (uint32, error) {
	hash := crc32.NewIEEE()
	f, err := os.Open(path)
	if err != nil {
		return 0, err
	}

	defer f.Close()

	buf := make([]byte, 8192)
	for {
		// read a chunk
		n, err := f.Read(buf)
		if err != nil && err != io.EOF {
			return 0, err
		}
		if n == 0 {
			break
		}

		// write a chunk
		if _, err := hash.Write(buf[:n]); err != nil {
			return 0, err
		}
	}

	return hash.Sum32(), nil
}

// Global variables
var server string
var identity string
var key string

func readEnvironment() {
	server = os.Getenv("SDS_SERVER")
	identity = os.Getenv("SDS_IDENTITY")
	key = os.Getenv("SDS_KEY")
}

func parseCommandLine() {
	flag.StringVar(&server, "server", server, "")
	flag.StringVar(&identity, "identity", identity, "")
	flag.StringVar(&key, "key", key, "")
	flag.Parse()
}

func computeAuthInfo() string {
    if key == "" || identity == "" {
        return ""
    }
    timestamp := strconv.Itoa(int(time.Now().Unix()))
    hash := md5.New()
    io.WriteString(hash, identity+timestamp+key)
    authHash := hex.EncodeToString(hash.Sum(nil))
    return "user="+identity+"&hash="+authHash+"&timestamp="+timestamp
}

func usage() {
	fmt.Println("USAGE: sds -server <SDS distribution server> -identity <username> -key <access-key> <command>")
	fmt.Println()
	fmt.Println("   server:       Names the server to fetch packets from.")
	fmt.Println("                 Must be an HTTP-URL like http://sds.foo.com")
	fmt.Println("                 (An environment variable named SDS_SERVER can also be used")
	fmt.Println("   identity:     Username used for authentication. This is optional for public packages.")
	fmt.Println("                 (An environment variable named SDS_IDENTITY can also be used")
	fmt.Println("   key:          Access code used for authentication. This is optional for public packages.")
	fmt.Println("                 (An environment variable named SDS_KEY can also be used")
	fmt.Println()
	fmt.Println("Commands:")
	fmt.Println()
	fmt.Println(" remote <package?>")
	fmt.Println("   Lists all packages or all versions of a package found on the server.")
	fmt.Println()
	fmt.Println(" push <package> <file>")
	fmt.Println("   Uploads the given file as new version of the given package.")
	fmt.Println()
	fmt.Println(" pull <package> <version?>")
	fmt.Println("   Synchronizes the local directory to contain all contents of the given package.")
	fmt.Println("   If a version is specified, this version will be used, otherwise the newest version will be used.")
	fmt.Println()
	fmt.Println(" verify <package> <version?>")
	fmt.Println("   Simulates a synchronization of the local directory against the given package.")
	fmt.Println("   No changes will be made. If no version is given, the latest will be used.")
	fmt.Println()
	fmt.Println(" monkey <package> <version?>")
	fmt.Println("   Performs a monkey patch for the local directory against the given package.")
	fmt.Println("   This will ask which modifications should be loaded and which shouldn't.")
	fmt.Println()
}

func push(pack string, file string) {
	fi, err := os.Open(file)
	if err != nil {
		panic(err)
	}

	hash, err := md5File(file)
	if err != nil {
		panic(err)
	}

	res, err := http.Post(server+"/artifacts/"+pack+"?contenthash="+hash+"&"+computeAuthInfo(), "application/zip", fi)
	if err != nil {
		panic(err)
	}
	fmt.Println(res.Status)
}

func listRemoteVersions(pack string) {
	fmt.Println("Remote Versions of: " + pack)
	fmt.Println("------------------------------------------")
	type Version struct {
		Artifact, Name, Date, Size string
	}
	type Result struct {
		Error    bool
		Message  string
		Versions []Version
	}
	res, err := http.Get(server + "/artifacts/" + pack+"?"+computeAuthInfo())
	if err != nil {
		panic(err)
	}
	defer res.Body.Close()
	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		panic(err)
	}

	var data Result
	err = json.Unmarshal(body, &data)
	if err != nil {
		fmt.Printf("%T\n%s\n%#v\n", err, err, err)
		switch v := err.(type) {
		case *json.SyntaxError:
			fmt.Println(string(body[v.Offset-40 : v.Offset]))
		}
	}
	if data.Error {
		fmt.Println(data.Message)
		return
	}
	for _, version := range data.Versions {
		fmt.Printf("Version: %s Release-Date: %s Size: %s", version.Name, version.Date, version.Size)
		fmt.Println()
	}
	fmt.Println()
}

func listRemotePackages() {
	fmt.Println("Remote Packages")
	fmt.Println("------------------------------------------")
	type Artifact struct {
		Name string
	}
	type Result struct {
		Error     bool
		Message   string
		Artifacts []Artifact
	}
	res, err := http.Get(server + "/artifacts"+"?"+computeAuthInfo())
	if err != nil {
		panic(err)
	}
	defer res.Body.Close()
	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		panic(err)
	}

	var data Result
	err = json.Unmarshal(body, &data)
	if err != nil {
		fmt.Printf("%T\n%s\n%#v\n", err, err, err)
		switch v := err.(type) {
		case *json.SyntaxError:
			fmt.Println(string(body[v.Offset-40 : v.Offset]))
		}
	}
	if data.Error {
		fmt.Println(data.Message)
		return
	}
	for _, artifact := range data.Artifacts {
		fmt.Println(artifact.Name)
	}
	fmt.Println()
}

type RemoteFile struct {
	Name string
	Crc  uint32
	Size int64
}

var numFilesChecked = 0
var numFilesAdded = 0
var numFilesChanged = 0
var numFilesDeleted = 0
var numErrors = 0
var numFailures = 0
var numSkipped = 0

func pull(pack string, version string, acceptsChange ChangeHandler) {
	fmt.Println("Synchronizing: " + pack)

	type Result struct {
		Error   bool
		Message string
		Version string
		Files   []RemoteFile
	}
	res, err := http.Get(server + "/artifacts/" + pack + "/" + version + "/_index?"+computeAuthInfo())
	if err != nil {
		panic(err)
	}
	defer res.Body.Close()
	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		panic(err)
	}

	var data Result
	err = json.Unmarshal(body, &data)
	if err != nil {
		fmt.Printf("%T\n%s\n%#v\n", err, err, err)
		switch v := err.(type) {
		case *json.SyntaxError:
			fmt.Println(string(body[v.Offset-40 : v.Offset]))
		}
	}
	if data.Error {
		fmt.Println(data.Message)
		return
	}
	fmt.Println("Version: " + data.Version)
	fmt.Println("------------------------------------------")
	expectedFiles := make(map[string]bool)
	for _, f := range data.Files {
		if !strings.HasSuffix(f.Name, ".sdsignore") {
			numFilesChecked++
			var filePath = strings.Replace(f.Name, "/", string(os.PathSeparator), -1)
			pullFile(pack, version, f, filePath, 5, acceptsChange)
		}
		expectedFiles[f.Name] = true
	}
	scanForUnexpectedFiles("."+string(os.PathSeparator), "", acceptsChange, expectedFiles)
	fmt.Println()
	fmt.Println("------------------------------------------")
	if numFailures > 0 {
		fmt.Println("Operation Failed! ")
	} else if numErrors > 0 {
		fmt.Println("Operation had errors which could be fixed! ")
	} else {
		fmt.Println("Operation was successful! ")
	}
    fmt.Printf(" - Files checked:......%5d", numFilesChecked)
    fmt.Println()
    fmt.Printf(" - New Files:..........%5d", numFilesAdded)
    fmt.Println()
    fmt.Printf(" - Changed Files:......%5d", numFilesChanged)
    fmt.Println()
    fmt.Printf(" - Deleted Files:......%5d", numFilesDeleted)
    fmt.Println()
    fmt.Printf(" - Changes Skipped:....%5d", numSkipped)
    fmt.Println()
    fmt.Printf(" - Errors:.............%5d", numErrors)
    fmt.Println()
    fmt.Printf(" - Failures:...........%5d", numFailures)
    fmt.Println()
	fmt.Println("------------------------------------------")
}

func pullFile(pack string, version string, file RemoteFile, filePath string, retries int, acceptsChange ChangeHandler) {
	if retries <= 0 {
		numFailures++
		fmt.Println("Failed to update file: " + file.Name)
		return
	}

	fi, err := os.Stat(filePath)
	if err != nil {
		numFilesAdded++
		fmt.Println("+ " + file.Name)
		err = os.MkdirAll(filepath.Dir(filePath), 0755)
		if err != nil {
			fmt.Println("Cannot create parent directories for: " + file.Name)
			return
		}
		if !acceptsChange() {
			numSkipped++
			return
		}
		load(pack, version, file.Name, filePath)
		pullFile(pack, version, file, filePath, retries-1, acceptsChange)
		return
	} else if fi.Size() != file.Size {
		numFilesChanged++
		fmt.Println("> " + file.Name)
		if !acceptsChange() {
			numSkipped++
			return
		}
		load(pack, version, file.Name, filePath)
		pullFile(pack, version, file, filePath, retries-1, acceptsChange)
		return
	} else {
		sum, err := crcFile(filePath)
		if err != nil || sum != file.Crc {
			numFilesChanged++
			fmt.Println("* " + file.Name)
			if !acceptsChange() {
				numSkipped++
				return
			}
			load(pack, version, file.Name, filePath)
			pullFile(pack, version, file, filePath, retries-1, acceptsChange)
			return
		}
	}
}

func scanForUnexpectedFiles(dir string, prefix string, acceptsChange ChangeHandler, expectedSet map[string]bool) {
	files, _ := ioutil.ReadDir(dir)
	for _, f := range files {
		relativePath := prefix + f.Name()
		if !expectedSet[relativePath+".sdsignore"] {
			if relativePath != "trash" {
				if f.IsDir() {
					scanForUnexpectedFiles(relativePath+string(os.PathSeparator),
						prefix+f.Name()+"/",
						acceptsChange,
						expectedSet)
				} else if !expectedSet[relativePath] {
					fmt.Println("- " + relativePath)
					numFilesDeleted++
					if acceptsChange() {
						safeDelete(dir + f.Name())
					} else {
						numSkipped++
					}
				}
			}
		}
	}
}

func safeDelete(file string) {
	if strings.Contains(file, "sds") {
		return
	}
	trashPath := "trash" + string(os.PathSeparator) + file
	err := os.MkdirAll(filepath.Dir(trashPath), 0755)
	if err != nil {
		numFailures++
		fmt.Printf("Failed to create trash directory! Cannot delete: %s - %v", file, err)
		fmt.Println()
		return
	}
	err = os.Rename(file, trashPath)
	if err != nil {
		numFailures++
		fmt.Printf("Failed to move to trash: %s - %v", file, err)
		fmt.Println()
		return
	}
}

func load(pack string, version string, file string, filePath string) {
	out, err := os.Create(filePath)
	if err != nil {
		numErrors++
		fmt.Printf("Cannot create: %s: %v", filePath, err)
		fmt.Println()
		return
	}
	defer out.Close()
	req, err := http.NewRequest("GET", server+"/artifacts/"+pack+"/"+version+"/"+file+"?"+computeAuthInfo(), nil)
	if err != nil {
		numErrors++
		fmt.Printf("Cannot create request for: %s: %v", file, err)
		fmt.Println()
		return
	}
	req.Close = true
	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		numErrors++
		fmt.Printf("Server error for: %s: %v", file, err)
		fmt.Println()
		return
	}
	defer resp.Body.Close()
	_, err = io.Copy(out, resp.Body)
	if err != nil {
		numErrors++
		fmt.Printf("Error downloading: %s: %v", file, err)
		fmt.Println()
		return
	}
}

type ChangeHandler func() bool

func pullHandler() bool {
	return true
}

func verifyHandler() bool {
	return false
}

func monkeyHandler() bool {
	fmt.Print("Should I perform this change (y/n)? ")
	var buf string
	fmt.Scan(&buf)
	if buf == "y" {
		return true
	}
	fmt.Println("Skipping change...")
	return false
}

func main() {
	readEnvironment()
	parseCommandLine()
	if identity != "" {
		fmt.Printf("Software Distribution System Version 1.0 (%s@%s)", identity, server)
	} else {
		fmt.Printf("Software Distribution System Version 1.0 (%s)", server)
	}
	fmt.Println()
	fmt.Println()

	var cmd = flag.Arg(0)
	switch cmd {
	case "push":
		push(flag.Arg(1), flag.Arg(2))
		return
	case "remote":
		if len(flag.Args()) == 2 {
			listRemoteVersions(flag.Arg(1))
		} else {
			listRemotePackages()
		}
		return
	case "pull":
		if len(flag.Args()) == 3 {
			pull(flag.Arg(1), flag.Arg(2), pullHandler)
		} else {
			pull(flag.Arg(1), "latest", pullHandler)
		}
		return
	case "verify":
		if len(flag.Args()) == 3 {
			pull(flag.Arg(1), flag.Arg(2), verifyHandler)
		} else {
			pull(flag.Arg(1), "latest", verifyHandler)
		}
		return
	case "monkey":
		if len(flag.Args()) == 3 {
			pull(flag.Arg(1), flag.Arg(2), monkeyHandler)
		} else {
			pull(flag.Arg(1), "latest", monkeyHandler)
		}
		return
	default:
		usage()
	}
}
