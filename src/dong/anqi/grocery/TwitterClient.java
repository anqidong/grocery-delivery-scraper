package dong.anqi.grocery;

import twitter4j.DirectMessage;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterClient {
  private static final TwitterFactory factory = createTwitterFactory();
  private static TwitterFactory createTwitterFactory() {
    Utils.Credentials accessToken = Utils.readCredentials("creds/twitter-access.creds");
    Utils.Credentials apiKey = Utils.readCredentials("creds/twitter-consumer.creds");

    Configuration conf = new ConfigurationBuilder()
        .setDebugEnabled(true)
        .setOAuthConsumerKey(apiKey.user)
        .setOAuthConsumerSecret(apiKey.pass)
        .setOAuthAccessToken(accessToken.user)
        .setOAuthAccessTokenSecret(accessToken.pass)
        .build();

    return new TwitterFactory(conf);
  }

  private Twitter twitterInstance = factory.getInstance();

  private static final int MAX_DM_CHARS = 1000;

  private static final long DEFAULT_USER_ID = 2783502499L;
  public boolean sendDirectMessage(String message) {
    try {
      if (message.length() > MAX_DM_CHARS) {
        message = message.substring(0, MAX_DM_CHARS);
      }

      DirectMessage result = twitterInstance.sendDirectMessage(DEFAULT_USER_ID, message);
      return true;
    } catch (TwitterException te) {
      te.printStackTrace();
    }
    return false;
  }

  public static void main(String[] args) {
    boolean successful = new TwitterClient().sendDirectMessage("test from refactored program");
    System.exit(successful ? 0 : -1);
  }
}
