# JDime  -  Structured Merge with Auto-Tuning

[![Build Status](https://travis-ci.org/se-passau/jdime.svg?branch=master)](https://travis-ci.org/se-passau/jdime) (master)  
[![Build Status](https://travis-ci.org/se-passau/jdime.svg?branch=develop)](https://travis-ci.org/se-passau/jdime?branch=develop) (develop)

## License & Copyright
* Copyright (C) 2013-2014 Olaf Lessenich
* Copyright (C) 2014-2017 University of Passau, Germany
> Authors: Olaf Lessenich, Georg Seibt

All rights reserved.

JDime is covered by the GNU Lesser General Public License.
The full license text is distributed with this software. See the `LICENSE` file.

## Tools Used:
JDime uses these tools/libraries:

* ExtendJ (https://bitbucket.org/jastadd/jastaddj)
> Commit [077922e](https://bitbucket.org/extendj/extendj/commits/077922ed7dc8277794d23b3464017c4a40568375)
> Copyright (c) 2005-2008, Torbjörn Ekman
> Copyright (c) 2005-2017, ExtendJ Committers
> ExtendJ is covered by the Modified BSD License.
> The full license text is distributed with this software.
> See the file licenses/ExtendJ-BSD.

The changes that were made to ExtendJ are shipped with this software
and covered by the Modified BSD License.
See patches/ExtendJ for the changes,
and patches/ExtendJ/LICENSE for the license text.

## System Requirements:
* JDK8 + JavaFX
* git (http://git-scm.com/)
* libgit2 (https://libgit2.github.com/)
* glpk (http://www.gnu.org/software/glpk/) __=4.56__
* glpk-java (http://glpk-java.sourceforge.net/) __=4.56__

__Debian/Ubuntu:__
`apt-get install git libgit2-dev libglpk-dev libglpk-java`

__Redhat/Fedora:__
`dnf install git libgit2 glpk`
glpk-java has to be installed manually

__Suse/OpenSuse:__
`zypper install git libgit2 glpk`
glpk-java has to be installed manually

__FreeBSD:__
`pkg install git openjfx8-devel libgit2 glpk`
glpk-java has to be installed manually

## Installation:
Clone the repository using `git clone $URL`.

JDime uses gradle as a build system.
To avoid version mismatches with already installed instances of gradle, you can use the supplied gradle wrapper `gradlew` that bootstraps the right version of gradle automatically.

Use `./gradlew -Pglpk=PATH -PglpkJava=PATH installDist` providing the paths to the `glpk_X_XX` and `glpk_X_XX_java` 
native libraries appropriate for your platform. `X_XX` refers to the version of glpk, currently __version 4.56__ is required.

After running the command given above the directory `build/install/JDime/bin` will contain Unix and Windows scripts
that you can use to start the application.

Instead of passing the library paths as command line arguments, you can also store them to a `gradle.properties` file, which looks like this:
```
glpkJava=PATH
glpk=PATH
```

## Usage:
The input versions are passed to JDime as command line arguments. To perform a three-way merge, JDime is invoked as follows:

`JDime --mode [unstructured|structured|autotuning] --output [file/directory] <leftVersion> <baseVersion> <rightVersion>`

Run `JDime --help` to show more extensive usage information.
