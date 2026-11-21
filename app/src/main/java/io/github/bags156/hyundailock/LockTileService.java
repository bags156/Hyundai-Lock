package io.github.bags156.hyundailock;

import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** A Quick Settings tile that locks the car. There is deliberately no unlock tile. */
public final class LockTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        render();
    }

    @Override
    public void onClick() {
        super.onClick();
        AppSettings settings = new AppSettings(this);
        if (!settings.ready()) {
            render();
            return;
        }
        CarControl.get(this).run(CarControl.Command.LOCK, CarControl.Source.TILE);
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_ACTIVE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle("Locking…");
            }
            tile.updateTile();
        }
    }

    private void render() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        AppSettings settings = new AppSettings(this);
        CarControl car = CarControl.get(this);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_stat_lock));
        tile.setLabel("Lock car");
        String subtitle;
        if (!settings.ready()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            subtitle = "Sign in first";
        } else if (car.busy() != null) {
            tile.setState(Tile.STATE_ACTIVE);
            subtitle = car.busy();
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            subtitle = settings.lastResult();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(subtitle);
        }
        tile.updateTile();
    }
}
