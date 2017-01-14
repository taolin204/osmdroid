package org.osmdroid.gpkg;

import android.content.Context;
import android.os.Build;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.tileprovider.IMapTileProviderCallback;
import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.modules.IFilesystemCache;
import org.osmdroid.tileprovider.modules.INetworkAvailablityCheck;
import org.osmdroid.tileprovider.modules.MapTileDownloader;
import org.osmdroid.tileprovider.modules.MapTileFilesystemProvider;
import org.osmdroid.tileprovider.modules.MapTileSqlCacheProvider;
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck;
import org.osmdroid.tileprovider.modules.SqlTileWriter;
import org.osmdroid.tileprovider.modules.TileWriter;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.BoundingBox;

import java.io.File;
import java.util.Iterator;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.projection.Projection;
import mil.nga.geopackage.projection.ProjectionFactory;
import mil.nga.geopackage.projection.ProjectionTransform;
import mil.nga.geopackage.tiles.user.TileDao;

/**
 * GeoPackage +
 * created on 1/5/2017.
 *
 * @author Alex O'Ree
 */

public class GeoPackageProvider extends MapTileProviderArray implements IMapTileProviderCallback {

    protected GeoPackageMapTileModuleProvider geopackage;
    protected IFilesystemCache tileWriter;

    public GeoPackageProvider(File[] db, Context context) {
        this(new SimpleRegisterReceiver(context), new NetworkAvailabliltyCheck(context),
            TileSourceFactory.DEFAULT_TILE_SOURCE, context, null, db);
    }


    public GeoPackageProvider(final IRegisterReceiver pRegisterReceiver,
                              final INetworkAvailablityCheck aNetworkAvailablityCheck, final ITileSource pTileSource,
                              final Context pContext, final IFilesystemCache cacheWriter, File[] databases) {


        super(pTileSource, pRegisterReceiver);


        if (cacheWriter != null) {
            tileWriter = cacheWriter;
        } else {
            if (Build.VERSION.SDK_INT < 10) {
                tileWriter = new TileWriter();
            } else {
                tileWriter = new SqlTileWriter();
            }
        }

        if (Build.VERSION.SDK_INT < 10) {
            final MapTileFilesystemProvider fileSystemProvider = new MapTileFilesystemProvider(
                pRegisterReceiver, pTileSource);
            mTileProviderList.add(fileSystemProvider);
        } else {
            final MapTileSqlCacheProvider cachedProvider = new MapTileSqlCacheProvider(pRegisterReceiver, pTileSource);
            mTileProviderList.add(cachedProvider);
        }
        geopackage = new GeoPackageMapTileModuleProvider(databases, pContext, tileWriter);
        mTileProviderList.add(geopackage);

        final MapTileDownloader downloaderProvider = new MapTileDownloader(pTileSource, tileWriter,
            aNetworkAvailablityCheck);
        mTileProviderList.add(downloaderProvider);
    }

    public GeoPackageMapTileModuleProvider geoPackageMapTileModuleProvider(){
        return geopackage;
    }


    @Override
    public IFilesystemCache getTileWriter() {
        return tileWriter;
    }

    @Override
    public void detach() {
        //https://github.com/osmdroid/osmdroid/issues/213
        //close the writer
        if (tileWriter != null)
            tileWriter.onDetach();
        tileWriter = null;
        super.detach();
    }

    public ITileSource getTileSource(String database, String table) {
        Iterator<GeoPackage> iterator = geopackage.tileSources.iterator();
        while (iterator.hasNext()){
            GeoPackage next = iterator.next();
            if (next.getName().equalsIgnoreCase(database)) {
                //found the database
                if (next.getTileTables().contains(table)) {
                    //find the tile table
                    TileSourceBounds t = new TileSourceBounds();
                    TileDao tileDao = next.getTileDao(table);
                    mil.nga.geopackage.BoundingBox boundingBox = tileDao.getBoundingBox();

                    Projection webmercator = ProjectionFactory.getProjection(0);
                    ProjectionTransform transformation = tileDao.getProjection().getTransformation(webmercator);
                    boundingBox=transformation.transform(boundingBox);
                    t.bounds=new BoundingBox(boundingBox.getMaxLatitude(),boundingBox.getMaxLongitude(),boundingBox.getMinLatitude(),boundingBox.getMinLongitude());
                    t.maxzoom=(int)tileDao.getMaxZoom();
                    t.minzoom=(int)tileDao.getMinZoom();
                    return new XYTileSource(table, t.minzoom, t.maxzoom, 256, "", new String[]{database});
                }
            }
        }

        return null;
    }

    /**
     * returns null if the database or tile table cannot be found
     * @return
     */
    public TileSourceBounds getTileSourceBounds() {
        Iterator<GeoPackage> iterator = geopackage.tileSources.iterator();
        while (iterator.hasNext()){
            GeoPackage next = iterator.next();
            if (next.getName().equals(((OnlineTileSourceBase)getTileSource()).getBaseUrl())) {
                //found the database
                if (next.getTileTables().contains(getTileSource().name())) {
                    //find the tile table
                    TileSourceBounds t = new TileSourceBounds();
                    TileDao tileDao = next.getTileDao(getTileSource().name());
                    mil.nga.geopackage.BoundingBox boundingBox = tileDao.getBoundingBox();

                    Projection webmercator = ProjectionFactory.getProjection(0);
                    ProjectionTransform transformation = tileDao.getProjection().getTransformation(webmercator);
                    boundingBox=transformation.transform(boundingBox);
                    t.bounds=new BoundingBox(boundingBox.getMaxLatitude(),boundingBox.getMaxLongitude(),boundingBox.getMinLatitude(),boundingBox.getMinLongitude());
                    t.maxzoom=(int)tileDao.getMaxZoom();
                    t.minzoom=(int)tileDao.getMinZoom();
                    return t;
                }
            }
        }
        return null;

    }

    public static class TileSourceBounds{
        public BoundingBox bounds;
        public int minzoom;
        public int maxzoom;
    }
}
