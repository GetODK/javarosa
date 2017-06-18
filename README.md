# ODK JavaRosa
![Platform](https://img.shields.io/badge/platform-Java-blue.svg)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build status](https://circleci.com/gh/opendatakit/javarosa.svg?style=shield&circle-token=:circle-token)](https://circleci.com/gh/opendatakit/javarosa)
[![Slack status](http://slack.opendatakit.org/badge.svg)](http://slack.opendatakit.org)

JavaRosa is a Java library for rendering forms that are compliant with [ODK XForms spec](http://opendatakit.github.io/xforms-spec). It is at the heart of many of the ODK tools. ODK JavaRosa is a fork of [JavaRosa](https://bitbucket.org/javarosa/javarosa/wiki/Home) 1.0 that has been modified to NOT run on J2ME devices. The key differences are:

* Regularly updated to ensure spec compliance
* KoBo additional instance defn. and filter paths
* remember all bind attributes and any additional attributes on `<input>`, `<select>`, `<group>`, etc. statements
* numerous enhancements and contributions from SurveyCTO and others.
* J2ME sub-projects removed

ODK JavaRosa is part of Open Data Kit (ODK), a free and open-source set of tools which help organizations author, field, and manage mobile data collection solutions. Learn more about the Open Data Kit project and its history [here](https://opendatakit.org/about/) and read about example ODK deployments [here](https://opendatakit.org/about/deployments/).

* ODK website: [https://opendatakit.org](https://opendatakit.org)
* ODK forum: [https://forum.opendatakit.org](https://forum.opendatakit.org)
* ODK developer Slack chat: [http://slack.opendatakit.org](http://slack.opendatakit.org) 
* ODK developer Slack archive: [http://opendatakit.slackarchive.io](http://opendatakit.slackarchive.io) 
* ODK developer wiki: [https://github.com/opendatakit/opendatakit/wiki](https://github.com/opendatakit/opendatakit/wiki)

## Setting up your development environment

1. Fork the javarosa project ([why and how to fork](https://help.github.com/articles/fork-a-repo/))

1. Clone your fork of the project locally. At the command line:

        git clone https://github.com/YOUR-GITHUB-USERNAME/javarosa

We recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) for development. On the welcome screen, click `Import Project`, navigate to your javarosa folder, and select the `build.gradle` file. Use the defaults through the wizard. Once the project is imported, IntelliJ may ask you to update your remote maven repositories. Follow the instructions to do so. 
 
## Building the project
 
To build the project, go to the `View` menu, then `Tool Windows > Gradle`. `build` will be in `odk-javarosa > Tasks > build > build`. Double-click `build` to package the application. This Gradle task will now be the default action in your `Run` menu. 

To package a jar, use the `jar` Gradle task.

## Distributing the jar

We use [OSSRH](http://central.sonatype.org/pages/ossrh-guide.html) to distribute this jar to a few public Maven and Gradle repositories. This process is [outlined here](http://central.sonatype.org/pages/apache-maven.html). Basically, run `mvn -s secrets.xml clean deploy`.

While we use gradle as our default build tool for all ODK tools (including this one), we use maven for distributing the jar because OSSRH's gradle support is unreliable (e.g., snapshots don't always update). This means version and dependency changes must be made in both `build.gradle` and `pom.xml`.

One deviation from OSSRH's documentation is that we use the latest versions of the maven plugins in `pom.xml`. We also use `gpg2` and our `secrets.xml` includes `gpg.homedir`, `gpg.keyname`, and `gpg.passphrase` so core committers can easily deploy. All that is needed in the `gpg.homedir` is `private-keys-v1.d` and `pubring.gpg`.

```
<!-- secrets.xml -->
<settings>
	<servers>
		<server>
			<id>ossrh</id>
			<username>opendatakit</username>
			<password>very-secure-password</password>
		</server>
	</servers>
	<profiles>
		<profile>
			<id>ossrh</id>
			<activation>
				<activeByDefault>true</activeByDefault>
			</activation>
			<properties>
				<gpg.executable>gpg2</gpg.executable>
				<gpg.homedir>/path/to/javarosa/gpg/folder</gpg.homedir>
				<gpg.keyname>1234ABCD</gpg.keyname>
				<gpg.passphrase>very-secure-passphrase</gpg.passphrase>
			</properties>
		</profile>
	</profiles>
</settings>
```

## Contributing code
Any and all contributions to the project are welcome. ODK JavaRosa is used across the world primarily by organizations with a social purpose so you can have real impact!

If you're ready to contribute code, see [the contribution guide](CONTRIBUTING.md).

## Downloading builds
Per-commit debug builds can be found on [CircleCI](https://circleci.com/gh/opendatakit/javarosa). Login with your GitHub account, click the build you'd like, then find the JAR in the Artifacts tab under $CIRCLE_ARTIFACTS.
