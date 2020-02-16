package csokicraft.bukkit.heist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType.PrimitivePersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class SimpleHeist extends JavaPlugin{
	/* Config variables */
	/** List of weapon items */
	protected List<ItemStack> weaponsRegistry;
	/** How many cops need to be online during a heist? */
	protected int nrCops;
	/** How far you can get before the heist is cancelled? */
	protected int radius;
	/** Target types and rewards */
	protected Map<String, HeistReward> types=new HashMap<>();

	/* Internal variables */
	protected NamespacedKey HEIST_ITEM;
	protected HeistThread heist;
	protected BukkitTask task;
	
	private YamlLocale i18n;
	
	/* Event handlers */
	@Override
	public void onEnable(){
		super.onEnable();
		HEIST_ITEM=new NamespacedKey(this, "heist_item");
		
		var cfg=getConfig();
		weaponsRegistry=(List<ItemStack>) cfg.getList("weapons", Collections.emptyList());
		nrCops=cfg.getInt("cops", 3);
		radius=cfg.getInt("radius", 5);
		//cfg.getConfigurationSection("raidTypes");
		try{
			i18n=YamlLocale.getLocale(cfg.getString("lang"), this);
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}
	
	public void onSignCreated(SignChangeEvent e){
		var b=e.getBlock();
		if(isSign(b.getType()))
			return;
		if(!"[Heist]".equals(e.getLine(0))&&!"§2§k[Heist]".equals(e.getLine(0))&&!"§4§k[Heist]".equals(e.getLine(0)))
			return;
		if(types.containsKey(e.getLine(1))){
			e.setLine(0, "§2§k[Heist]");
			//TODO: cooldown display
			e.setLine(2, __("msg_ready"));
		}else
			e.setLine(0, "§4§k[Heist]");
	}
	
	public void onSignClicked(PlayerInteractEvent e){
		if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)||!isSign(e.getClickedBlock().getType()))
			return;
		Sign s=(Sign) e.getClickedBlock().getBlockData();
		if(!"§2§k[Heist]".equals(s.getLine(0)))
			return;
		startHeist(e.getPlayer(), s);
	}
	
	public void onPlayerJoined(PlayerJoinEvent e){
		removeCompass(e.getPlayer());
	}
	
	//TODO: cooldown tick
	
	/* Game logic */
	/** Heist compass */
	public void giveCompass(Player p){
		if(!p.hasPermission("simpleheist.cop")){
			p.sendMessage(__("err_not_cop"));
			return;
		}
		var i=p.getInventory();
		if(i.firstEmpty()<0){
			p.sendMessage(__("err_compass_fullinv"));
		}
		ItemStack compass=new ItemStack(Material.COMPASS);
		var meta=compass.getItemMeta();
		meta.getPersistentDataContainer().set(HEIST_ITEM, PrimitivePersistentDataType.INTEGER, 1);
		compass.setItemMeta(meta);
		i.addItem(compass);
		p.setCompassTarget(heist.loc);
		//TODO: maybe restore compass target?
	}
	
	public void removeCompass(Player p){
		var i=p.getInventory();
		var it=i.iterator();
		for(var is=it.next();it.hasNext();is=it.next()){
			if(is.getItemMeta().getPersistentDataContainer().get(HEIST_ITEM, PrimitivePersistentDataType.INTEGER)==1)
				it.remove();
		}
	}
	
	public void startHeist(Player p, Sign s){
		var reward=types.get(s.getLine(2));
		if(reward==null){
			getLogger().log(Level.WARNING, "No heist type: "+s.getLine(2));
			return;
		}
		if(p.hasPermission("simpleheist.cop")){
			p.sendMessage(__("err_cop_heist"));
			return;
		}
		if(heist!=null){
			p.sendMessage(__("err_heist_exists"));
			return;
		}
		//TODO: check cooldown
		var cops=Bukkit.getServer().getOnlinePlayers().stream().filter((q)->{return q.hasPermission("simpleheist.cop");}).collect(Collectors.toList());
		if(cops.size()<nrCops){
			p.sendMessage(String.format(__("err_no_cops"), nrCops, cops));
			return;
		}
		//TODO: check for weapon
		for(var cop:cops){
			cop.sendMessage(__("msg_heist_start"));
			giveCompass(cop);
		}
		heist=new HeistThread(p, s.getLocation(), reward);
		task=Bukkit.getServer().getScheduler().runTask(this, heist);
	}
	
	public void endHeist(){
		Bukkit.getServer().getOnlinePlayers().stream().filter((q)->{return q.equals(heist.player)||q.hasPermission("simpleheist.cop");}).forEach((p)->{
			p.sendMessage(__("msg_heist_end"));
			removeCompass(p);
		});
		task.cancel();
	}

	/* Misc functions */
	public static SimpleHeist getInstance(){
		return getPlugin(SimpleHeist.class);
	}
	
	public String __(String s){
		return i18n.translate(s);
	}

	@SuppressWarnings("deprecation")
	private boolean isSign(Material type){
		for(Material mat:new Material[]{
				//*
				Material.ACACIA_SIGN, Material.ACACIA_WALL_SIGN,
				Material.BIRCH_SIGN, Material.BIRCH_WALL_SIGN,
				Material.DARK_OAK_SIGN, Material.DARK_OAK_WALL_SIGN,
				Material.JUNGLE_SIGN, Material.JUNGLE_WALL_SIGN,
				Material.LEGACY_SIGN, Material.LEGACY_WALL_SIGN, Material.LEGACY_SIGN_POST,
				Material.OAK_SIGN, Material.OAK_WALL_SIGN,
				Material.SPRUCE_SIGN, Material.SPRUCE_WALL_SIGN,
				//*/
				//Material.SIGN, Material.SIGN_POST, Material.WALL_SIGN
			})
			if(mat.equals(type))
				return true;
		return false;
	}
}
