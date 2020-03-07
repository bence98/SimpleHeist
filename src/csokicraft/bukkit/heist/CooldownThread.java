package csokicraft.bukkit.heist;

import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import static csokicraft.bukkit.heist.SimpleHeist.getInstance;

public class CooldownThread implements Runnable{
	private final Sign sign;
	private final int maxCooldown;
	private int bukkitTask;
	private int cooldownSec;
	
	public CooldownThread(Sign s, int cool){
		sign=s;
		maxCooldown=cool;
	}
	
	public boolean schedule(){
		if(cooldownSec>0)
			return false;
		cooldownSec=maxCooldown;
		bukkitTask=Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(SimpleHeist.getInstance(), this, 0, 20);
		return true;
	}
	
	public void deschedule(){
		Bukkit.getServer().getScheduler().cancelTask(bukkitTask);
	}

	@Override
	public void run(){
		cooldownSec--;
		if(cooldownSec==0){
			sign.setLine(2, getInstance().__("msg_ready"));
			sign.update();
			deschedule();
		}else{
			sign.setLine(2, formatMsg(getInstance().__("msg_cooldown")));
			sign.update();
		}
	}

	String formatMsg(String msg){
		return String.format(msg, cooldownSec/60, cooldownSec%60);
	}
}
