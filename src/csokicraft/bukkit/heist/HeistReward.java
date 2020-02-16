package csokicraft.bukkit.heist;

import java.util.HashMap;
import java.util.Random;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class HeistReward{
	/** ItemStack - percentage chance pairs */
	protected HashMap<ItemStack, Byte> rewards;
	
	/** Time needed to perform the heist (sec) */
	protected int difficulty;
	
	//TODO: cooldown time
	
	private Random rng=new Random();
	
	public HeistReward(int n){
		difficulty=n;
		rewards=new HashMap<>();
	}
	
	public void giveTo(Player p){
		rewards.forEach((is, b)->{
			if(rng.nextInt(100)>b)
				_giveTo(p, is);
		});
	}

	private void _giveTo(Player p, ItemStack is){
		var i=p.getInventory();
		if(i.firstEmpty()>0)
			i.addItem(is);
		else
			p.getWorld().dropItem(p.getLocation(), is);
	}
}
