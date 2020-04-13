package dong.anqi.grocery;

import twitter4j.DirectMessage;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterTestClient {
  public static void main(String[] args) {
    Utils.Credentials accessToken = Utils.readCredentials("creds/twitter-access.creds");
    Utils.Credentials apiKey = Utils.readCredentials("creds/twitter-consumer.creds");

    Configuration conf = new ConfigurationBuilder()
        .setDebugEnabled(true)
        .setOAuthConsumerKey(apiKey.user)
        .setOAuthConsumerSecret(apiKey.pass)
        .setOAuthAccessToken(accessToken.user)
        .setOAuthAccessTokenSecret(accessToken.pass)
        .build();

    Twitter twitter = new TwitterFactory(conf).getInstance();
    try {
      DirectMessage message = twitter.sendDirectMessage("SafeVarargs", "test from program");
      System.exit(0);
    } catch (TwitterException te) {
      te.printStackTrace();
      System.out.println("Failed to send a direct message: " + te.getMessage());
      System.exit(-1);
    }
  }
}
