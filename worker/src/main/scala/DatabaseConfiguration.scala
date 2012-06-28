
import com.avaje.ebean.config.GlobalProperties

class DatabaseConfiguration {
  val DatabaseDriverProperty = "datasource.default.databaseDriver"
  val DatabaseUrlProperty =    "datasource.default.databaseUrl"
  val UsernameProperty =       "datasource.default.username"
  val PasswordProperty =       "datasource.default.password"
  val DATABASE_URL = 		   "datasource.default.url"
   
  val databaseDriver = GlobalProperties.get(DatabaseDriverProperty, null) 
  val (databaseUrl, username, password) = readSettings()
    
  def readSettings() : (String, String, String) = {
	val databaseSetting = sys.props.get(DATABASE_URL)
	databaseSetting match {
      case Some(databaseInfo) => {
        val urlPattern = "\\w+://(\\w+):(\\w+)@([\\w-.]+)/(\\w+)".r
    
        val urlPattern(user, password, host, database) = databaseInfo
        ("jdbc:postgresql://"+host+"/"+database, user, password)
      }
      case None => {
        (GlobalProperties.get(DatabaseUrlProperty, null),
         GlobalProperties.get(UsernameProperty, null),
         GlobalProperties.get(PasswordProperty, null))
      }
	}
  }
}