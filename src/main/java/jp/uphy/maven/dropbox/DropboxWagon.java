/**
 * Copyright (C) 2013 uphy.jp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.uphy.maven.dropbox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.InputData;
import org.apache.maven.wagon.OutputData;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.StreamWagon;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.resource.Resource;
import org.codehaus.plexus.component.annotations.Component;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxClient.Downloader;
import com.dropbox.core.DbxClient.Uploader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuthNoRedirect;
import com.dropbox.core.DbxWriteMode;


/**
 * @author Yuhi Ishikura
 */
@Component(role = Wagon.class, hint = "dropbox")
public final class DropboxWagon extends StreamWagon {

  private static final String CLIENT_IDENTIFIER = "Maven Wagon Dropbox/1.0"; //$NON-NLS-1$
  private DbxClient client;

  /**
   * {@inheritDoc}
   */
  @Override
  protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
    if (getAuthenticationInfo() == null || getAuthenticationInfo().getUserName() == null) {
      throw new AuthenticationException("Authentication not set.\n" + usage()); //$NON-NLS-1$
    }
    if (this.client != null) {
      return;
    }

    final String appKey = getAuthenticationInfo().getUserName();
    final String appSecret = getAuthenticationInfo().getPrivateKey();
    String accessToken = getAuthenticationInfo().getPassword();

    final DbxRequestConfig config = new DbxRequestConfig(CLIENT_IDENTIFIER, Locale.getDefault().toString());
    final DbxAppInfo appInfo = new DbxAppInfo(appKey, appSecret);

    if (accessToken == null) {
      try {
        accessToken = requestAccessToken(config, appInfo);
      } catch (IOException e1) {
        throw new ConnectionException("Couldn't read line from standard input.", e1); //$NON-NLS-1$
      } catch (DbxException e) {
        throw new AuthenticationException("Failed to authenticate with Dropbox.", e); //$NON-NLS-1$
      }
      System.out.println("Access Token : " + accessToken); //$NON-NLS-1$
      System.out.println("You can make it faster by describing access token in your ~/.m2/settings.xml."); //$NON-NLS-1$
    }

    this.client = new DbxClient(config, accessToken);
  }

  @SuppressWarnings("nls")
  private String usage() {
    final StringWriter sw = new StringWriter();
    final PrintWriter pw = new PrintWriter(sw);
    pw.println("In your Maven setting file(~/.m2/settings.xml), insert followings:"); //$NON-NLS-1$
    pw.println("<settings>");
    pw.println("  <server>");
    pw.printf("    <id>%s</id>%n", getRepository().getId());
    pw.println("    <username>Dropbox App Key</username>");
    pw.println("    <privateKey>Dropbox App Secret</privateKey>");
    pw.println("    <password>Access Token(Optional)</password>");
    pw.println("  </server>");
    pw.println("</settings>");
    return sw.toString();
  }

  private static String requestAccessToken(final DbxRequestConfig config, final DbxAppInfo appInfo) throws DbxException, IOException {
    final DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);
    final String authorizeUrl = webAuth.start();
    final String code = readAuthorizationCodeFromConsole(authorizeUrl);
    final DbxAuthFinish authFinish = webAuth.finish(code);
    return authFinish.accessToken;
  }

  @SuppressWarnings("nls")
  private static String readAuthorizationCodeFromConsole(final String authorizeUrl) throws IOException {
    System.out.println("1. Go to: " + authorizeUrl);
    System.out.println("2. Click \"Allow\" (you might have to log in first)");
    System.out.println("3. Copy the authorization code.");
    System.out.print("Authorization Code>");
    return new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void closeConnection() throws ConnectionException {
    // do nothing
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void fillInputData(InputData inputData) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    final Resource resource = inputData.getResource();
    try {
      final String filepath = getAbsolutePath(resource.getName());
      final Downloader downloader = this.client.startGetFile(filepath, null);
      if (downloader == null) {
        throw new ResourceDoesNotExistException("File not exist:" + filepath); //$NON-NLS-1$
      }
      inputData.setInputStream(downloader.body);
      resource.setContentLength(downloader.metadata.numBytes);
      resource.setLastModified(downloader.metadata.lastModified.getTime());
    } catch (DbxException e) {
      throw new TransferFailedException("Failed to download.", e); //$NON-NLS-1$
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void fillOutputData(OutputData outputData) throws TransferFailedException {
    try {
      final String filepath = getAbsolutePath(outputData.getResource().getName());
      createParentDirectory(filepath);
      final Uploader uploader = this.client.startUploadFile(filepath, DbxWriteMode.force(), outputData.getResource().getContentLength());
      outputData.setOutputStream(new UploaderOutputStream(uploader));
    } catch (DbxException e) {
      throw new TransferFailedException("Failed to upload.", e); //$NON-NLS-1$
    }
  }

  private void createParentDirectory(final String filepath) throws DbxException {
    if (filepath == null) {
      throw new IllegalArgumentException();
    }
    final File file = new File(filepath);
    if (file.getParent() == null) {
      return;
    }
    this.client.createFolder(file.getParent());
  }

  private String getAbsolutePath(String name) {
    return getRepository().getBasedir() + "/" + name; //$NON-NLS-1$
  }

  static class UploaderOutputStream extends FilterOutputStream {

    private Uploader uploader;

    UploaderOutputStream(Uploader uploader) {
      super(uploader.getBody());
      this.uploader = uploader;
    }

    UploaderOutputStream(OutputStream out) {
      super(out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
      try {
        this.uploader.finish();
      } catch (DbxException e) {
        throw new IOException(e);
      }
      this.uploader.close();
    }
  }

}
