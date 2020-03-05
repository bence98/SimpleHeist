package csokicraft.bukkit.heist;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class HeistThread implements Runnable{
	final Player player;
	final Location loc;
	private final HeistReward reward;
	private int duration;

	public HeistThread(Player p, Location heistLoc, HeistReward heist){
		player=p;
		loc=heistLoc;
		reward=heist;
		duration=0;
	}

	@Override
	public void run(){
		if(player.getLocation().distance(loc)>SimpleHeist.getInstance().radius)
			SimpleHeist.getInstance().endHeist();
		else if(duration>=reward.difficulty*4){
			player.sendMessage(SimpleHeist.getInstance().__("msg_heist_success"));
			reward.giveTo(player);
			SimpleHeist.getInstance().endHeist();
		}else
			duration++;
	}

}
