package csokicraft.bukkit.heist;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class HeistReward implements ConfigurationSerializable{
	/** ItemStack - percentage chance pairs */
	protected HashMap<ItemStack, Integer> rewards;
	
	/** Time needed to perform the heist (sec) */
	protected int difficulty;
	
	//TODO: cooldown time
	
	private Random rng=new Random();
	
	public HeistReward(int n){
		difficulty=n;
		rewards=new HashMap<>();
	}
	
	@SuppressWarnings("unchecked")
	public HeistReward(Map<String, Object> m){
		difficulty=(Integer) m.get("difficulty");
		rewards=(HashMap<ItemStack, Integer>) m.get("rewards");
	}
	
	public void giveTo(Player p){
		rewards.forEach((is, b)->{
			if(rng.nextInt(100)>b)
				_giveTo(p, is);
		});
	}

	private void _giveTo(Player p, ItemStack is){
		PlayerInventory i=p.getInventory();
		if(i.firstEmpty()>0)
			i.addItem(is);
		else
			p.getWorld().dropItem(p.getLocation(), is);
	}

	@Override
	public Map<String, Object> serialize(){
		Map<String, Object> m=new HashMap<>();
		m.put("difficulty", difficulty);
		m.put("rewards", rewards);
		return m;
	}
}
