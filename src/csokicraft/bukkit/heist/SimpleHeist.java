package csokicraft.bukkit.heist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
//import org.bukkit.persistence.PersistentDataType.PrimitivePersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class SimpleHeist extends JavaPlugin implements Listener{
	/* Config variables */
	/** List of weapon items */
	protected List<ItemStack> weaponsRegistry;
	/** How many cops need to be online during a heist? */
	protected int nrCops;
	/** How far you can get before the heist is cancelled? */
	protected int radius;
	/** How much you need to wait between heists? */
	protected int cooldown;
	/** Target types and rewards */
	protected Map<String, Object> types=new HashMap<>();

	/* Internal variables */
	protected NamespacedKey HEIST_ITEM;
	protected HeistThread heist;
	protected int task;
	protected Map<Location, CooldownThread> cooldowns=new HashMap<>();
	
	private YamlLocale i18n;
	
	/* Event handlers */
	@Override
	public void onEnable(){
		super.onEnable();
		ConfigurationSerialization.registerClass(HeistReward.class);
		saveDefaultConfig();
		
		getServer().getPluginManager().registerEvents(this, this);
		HEIST_ITEM=new NamespacedKey(this, "heist_item");
		
		FileConfiguration cfg=getConfig();
		weaponsRegistry=(List<ItemStack>) cfg.getList("weapons", Collections.emptyList());
		nrCops=cfg.getInt("cops", 3);
		radius=cfg.getInt("radius", 5);
		cooldown=cfg.getInt("cooldown", 180);
		types=cfg.getConfigurationSection("raidTypes").getValues(false);
		try{
			i18n=YamlLocale.getLocale(cfg.getString("lang"), this);
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}
	
	@EventHandler
	public void onSignCreated(SignChangeEvent e){
		if(!e.getPlayer().hasPermission("csokicraft.heist.admin"))
			return;
		if(!"[Heist]".equals(e.getLine(0))&&!("§2"+ChatColor.BOLD+"[Heist]").equals(e.getLine(0))&&!("§4"+ChatColor.BOLD+"[Heist]").equals(e.getLine(0)))
			return;
		if(types.containsKey(e.getLine(1))){
			e.setLine(0, ("§2"+ChatColor.BOLD+"[Heist]"));
			e.setLine(2, __("msg_ready"));
		}else
			e.setLine(0, ("§4"+ChatColor.BOLD+"[Heist]"));
	}
	
	@EventHandler
	public void onSignClicked(PlayerInteractEvent e){
		if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)||!isSign(e.getClickedBlock().getType()))
			return;
		Sign s=(Sign) e.getClickedBlock().getState();
		if(!("§2"+ChatColor.BOLD+"[Heist]").equals(s.getLine(0)))
			return;
		startHeist(e.getPlayer(), s);
	}
	
	@EventHandler
	public void onPlayerJoined(PlayerJoinEvent e){
		removeCompass(e.getPlayer());
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!"heist".equalsIgnoreCase(label))
			return super.onCommand(sender, command, label, args);
		
		if(args.length<1)
			return false;
		
		switch (args[0]){
		case "help":
			sender.sendMessage(SimpleHeist.getInstance().__("desc_compass"));
			sender.sendMessage(SimpleHeist.getInstance().__("desc_stop"));
			return true;
		case "compass":
			if(sender instanceof Player)
				SimpleHeist.getInstance().giveCompass((Player) sender);
			else
				sender.sendMessage(SimpleHeist.getInstance().__("err_not_player"));
			return true;
		case "stop":
			if(!sender.hasPermission("csokicraft.heist.admin")){
				sender.sendMessage(SimpleHeist.getInstance().__("err_not_admin"));
				return true;
			}
			SimpleHeist.getInstance().endHeist();
			return true;
		default:
			return false;
		}
	}
	
	@EventHandler
	public void onBreakBlock(BlockBreakEvent e){
		Player p=e.getPlayer();
		Location l=e.getBlock().getLocation();
		if(cooldowns.containsKey(l)){
			if(p.hasPermission("csokicraft.heist.admin")){
				cooldowns.get(l).deschedule();
				cooldowns.remove(l);
			}else
				e.setCancelled(true);
		}
	}
	
	/* Game logic */
	/** Heist compass */
	public void giveCompass(Player p){
		if(heist==null)
			return;
		
		if(!p.hasPermission("csokicraft.heist.cop")){
			p.sendMessage(__("err_not_cop"));
			return;
		}
		PlayerInventory i=p.getInventory();
		if(i.firstEmpty()<0){
			p.sendMessage(__("err_compass_fullinv"));
		}
		ItemStack compass=new ItemStack(Material.COMPASS);
		ItemMeta meta=compass.getItemMeta();
		//meta.getPersistentDataContainer().set(HEIST_ITEM, PrimitivePersistentDataType.INTEGER, 1);
		meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
		compass.setItemMeta(meta);
		i.addItem(compass);
		p.setCompassTarget(heist.loc);
		//TODO: maybe restore compass target?
	}
	
	public void removeCompass(Player p){
		PlayerInventory i=p.getInventory();
		List<ItemStack> toRm=new ArrayList<>();
		for(ItemStack is:i){
			if(is==null||!is.hasItemMeta())
				continue;
			//if(is.getItemMeta().getPersistentDataContainer().get(HEIST_ITEM, PrimitivePersistentDataType.INTEGER)==1)
			if(is.getItemMeta().hasItemFlag(ItemFlag.HIDE_UNBREAKABLE))
				toRm.add(is);
		}
		for(ItemStack is:toRm)
			i.remove(is);
	}
	
	public void startHeist(Player p, Sign s){
		HeistReward reward=(HeistReward) types.get(s.getLine(1));
		if(reward==null){
			getLogger().log(Level.WARNING, "No heist type: "+s.getLine(2));
			return;
		}
		
		if(p.hasPermission("csokicraft.heist.cop")){
			p.sendMessage(__("err_cop_heist"));
			return;
		}
		
		if(heist!=null){
			p.sendMessage(__("err_heist_exists"));
			return;
		}
		
		//TODO: check for weapon
		
		List<? extends Player> cops=Bukkit.getServer().getOnlinePlayers().stream().filter((q)->{return q.hasPermission("csokicraft.heist.cop");}).collect(Collectors.toList());
		if(cops.size()<nrCops){
			p.sendMessage(String.format(__("err_no_cops"), nrCops, cops.size()));
			return;
		}
		
		CooldownThread w=createCooldownWorker(s);
		if(!w.schedule()){
			p.sendMessage(w.formatMsg(__("err_cooldown")));
			return;
		}
		
		heist=new HeistThread(p, s.getLocation(), reward);
		p.sendMessage(__("msg_heist_start_p"));
		for(Player cop:cops){
			cop.sendMessage(__("msg_heist_start"));
			giveCompass(cop);
		}
		task=Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, heist, 0, 5);
	}
	
	public void endHeist(){
		if(heist==null)
			return;
		
		Bukkit.getServer().getOnlinePlayers().stream().filter((q)->{return q.equals(heist.player)||q.hasPermission("csokicraft.heist.cop");}).forEach((p)->{
			p.sendMessage(__("msg_heist_end"));
			removeCompass(p);
		});
		Bukkit.getServer().getScheduler().cancelTask(task);
		heist=null;
	}

	public CooldownThread createCooldownWorker(Sign s){
		if(!cooldowns.containsKey(s.getLocation()))
			cooldowns.put(s.getLocation(), new CooldownThread(s, cooldown));
		return cooldowns.get(s.getLocation());
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
				/*
				Material.ACACIA_SIGN, Material.ACACIA_WALL_SIGN,
				Material.BIRCH_SIGN, Material.BIRCH_WALL_SIGN,
				Material.DARK_OAK_SIGN, Material.DARK_OAK_WALL_SIGN,
				Material.JUNGLE_SIGN, Material.JUNGLE_WALL_SIGN,
				Material.LEGACY_SIGN, Material.LEGACY_WALL_SIGN, Material.LEGACY_SIGN_POST,
				Material.OAK_SIGN, Material.OAK_WALL_SIGN,
				Material.SPRUCE_SIGN, Material.SPRUCE_WALL_SIGN,
				//*/
				Material.SIGN, Material.SIGN_POST, Material.WALL_SIGN
			})
			if(mat.equals(type))
				return true;
		return false;
	}
}
