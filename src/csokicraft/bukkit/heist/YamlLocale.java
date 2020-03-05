package csokicraft.bukkit.heist;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class YamlLocale{
	protected ConfigurationSection trs;
	protected final File cfgFile;
	
	protected YamlLocale(Configuration cfg, File f){
		trs=cfg.getConfigurationSection("locale");
		cfgFile=f;
	}
	
	public static YamlLocale getLocale(String lang, Plugin plugin) throws IOException, InvalidConfigurationException{
		File f=new File(plugin.getDataFolder(), lang+".yaml");
		if(!f.exists()){
			InputStream in=plugin.getResource(lang+".yaml");
			if(in==null)
				in=plugin.getResource("en.yaml");
			YamlConfiguration cfg=new YamlConfiguration();
			cfg.load(new InputStreamReader(in, "UTF-8"));
			return new YamlLocale(cfg, f).save();
		}
		YamlConfiguration cfg=new YamlConfiguration();
		cfg.load(f);
		return new YamlLocale(cfg, f);
	}
	
	public String translate(String key){
		return trs.isString(key)?trs.get(key).toString():"";
	}
	
	public YamlLocale save() throws IOException{
		YamlConfiguration cfg=new YamlConfiguration();
		cfg.set("locale", trs);
		cfg.save(cfgFile);
		return this;
	}
}
