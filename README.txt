wagon-dropbox
=============

This is a Maven Wagon which enables to deploy to Dropbox.

1. Register your Dropbox App and get "App key" and "App secret".

	https://www.dropbox.com/developers/apps

2. In your Maven POM file, write following repository and wagon extension.

	<build>
		<extensions>
			<extension>
				<groupId>jp.uphy.maven</groupId>
				<artifactId>wagon-dropbox</artifactId>
				<version>1.0.0</version>
			</extension>
		</extensions>
	</build>
	
	<pluginRepositories>
		<pluginRepository>
			<id>dropbox-uphy</id>
			<url>http://dl.dropboxusercontent.com/u/2047205/</url>
		</pluginRepository>
	</pluginRepositories>

	<distributionManagement>
		<repository>
			<!-- ID for binding Maven settings.xml -->
			<id>dropbox</id>
			<!-- Dropbox target directory path. -->
			<!-- 'dropbox:/' means root directory.  -->
			<url>dropbox:/your-dropbox-path</url>
		</repository>
	</distributionManagement>

3. Write authentication information in Maven settings.xml

   <server>
   	  <!-- Same as <id> in 2. -->
      <id>dropbox</id>
      <username>App key</username>
      <privateKey>App secret</privateKey>
      <password>Access Token(Optional)</password>
   </server>

4. Deploy

	mvn clean deploy

	If you omitted the Access Token in 3, this wagon ask you to authorize app with your browser and get it.
	After you will get Access Token, please update Maven settings.xml.
