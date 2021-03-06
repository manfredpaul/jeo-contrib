/* Copyright 2013 The jeo project. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jeo.nano;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

import java.io.OutputStream;
import java.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import io.jeo.filter.Filter;
import io.jeo.filter.Id;
import io.jeo.filter.Literal;
import io.jeo.filter.Property;
import io.jeo.filter.TypeOf;
import io.jeo.geom.Bounds;
import io.jeo.vector.FeatureAppendCursor;
import io.jeo.vector.FeatureCursor;
import io.jeo.vector.FeatureWriteCursor;
import io.jeo.vector.ListFeature;
import io.jeo.vector.MapFeature;
import org.easymock.IAnswer;
import org.easymock.IExpectationSetters;
import org.easymock.classextension.EasyMock;
import io.jeo.data.DataRepositoryView;
import io.jeo.data.Dataset;
import io.jeo.data.Driver;
import io.jeo.data.Handle;
import io.jeo.vector.VectorQuery;
import io.jeo.map.Style;
import io.jeo.map.View;
import io.jeo.render.Renderer;
import io.jeo.render.RendererFactory;
import io.jeo.render.RendererRegistry;
import io.jeo.tile.Tile;
import io.jeo.tile.TileDataset;
import io.jeo.tile.TilePyramid;
import io.jeo.vector.VectorDataset;
import io.jeo.data.Workspace;
import io.jeo.data.mem.MemVectorDataset;
import io.jeo.vector.Feature;
import io.jeo.vector.Field;
import io.jeo.vector.Schema;
import io.jeo.vector.SchemaBuilder;
import io.jeo.proj.Proj;
import org.osgeo.proj4j.CoordinateReferenceSystem;

public class MockServer {

    private final List<Object> mocks = new ArrayList<Object>();
    final NanoServer server;
    MemVectorDataset memoryLayer;
    private VectorDataset vectorLayer;
    private TileDataset tileLayer;
    private Workspace workspace;
    private final DataRepositoryView reg;
    private RendererRegistry rendererRegistry;
    private List<Handle<?>> workspaces = new ArrayList<Handle<?>>();
    private List<Handle<?>> styles = new ArrayList<Handle<?>>();

    private MockServer() {
        server = createMock(NanoServer.class);
        reg = createMock(DataRepositoryView.class);
        rendererRegistry = createMock(RendererRegistry.class);
    }

    public static MockServer create() {
        return new MockServer();
    }

    private <T> T createMock(Class<T> clazz) {
        T mock = EasyMock.createMock(clazz);
        mocks.add(mock);
        return mock;
    }

    private <T> T createNiceMock(Class<T> clazz) {
        T mock = EasyMock.createNiceMock(clazz);
        mocks.add(mock);
        return mock;
    }

    MockServer withFeature(String id, Object... kv) throws Exception {
        Map<String,Object> vals = new HashMap<String,Object>();
        for (int i = 0; i < kv.length; i+=2) {
            vals.put(kv[i].toString(), kv[i+1]);
        }
        Feature f = new MapFeature(id, vals);
        MemVectorDataset v = new MemVectorDataset();
        v.add(f);
        expect(vectorLayer.read((VectorQuery) anyObject())).andReturn(v.read(new VectorQuery())).once();
        
        return this;
    }

    MockServer withVectorLayer() throws Exception {
        withWorkspace();

        vectorLayer = createMock(VectorDataset.class);
        expect(workspace.get("bar")).andReturn(vectorLayer).once();
        expect(workspace.get((String) anyObject())).andReturn(null).anyTimes();
        vectorLayer.close();
        expectLastCall().once();

        return this;
    }

    MockServer withPointGeometry() throws Exception {
        expect(vectorLayer.crs()).andReturn(Proj.EPSG_4326);
        expect(vectorLayer.bounds()).andReturn(null);
        expect(vectorLayer.name()).andReturn("emptylayer");
        Schema schema = createMock(Schema.class);
        expect(vectorLayer.schema()).andReturn(schema);
        return this;
    }

    MockServer withNoFeatures() throws Exception {
        expect(vectorLayer.read(new VectorQuery().bounds(new Bounds(-180, 180, -90, 90))))
                .andReturn(FeatureCursor.empty()).once();

        return this;
    }

    MockServer withFeatureHavingId(String id) throws Exception {
        Feature f = new MapFeature(id);
        f.put("id", id);

        FeatureCursor c = createMock(FeatureCursor.class);
        expect(c.hasNext()).andReturn(true);
        expect(c.hasNext()).andReturn(true);
        expect(c.next()).andReturn(f);
        expect(c.hasNext()).andReturn(false);

        expect(vectorLayer.read(new VectorQuery().filter(new Id(new Literal(id))))).andReturn(c);
        expect(vectorLayer.read((VectorQuery) anyObject())).andReturn(FeatureCursor.empty()).anyTimes();
        c.close();
        expectLastCall().atLeastOnce();

        expect(vectorLayer.read(new VectorQuery().filter((Filter) anyObject())))
                .andReturn(FeatureCursor.empty()).anyTimes();

        return this;
    }

    MockServer withFeatureHavingIdForEdit(Feature feature, boolean expectSuccess) throws Exception {
        FeatureWriteCursor c = createMock(FeatureWriteCursor.class);
        expect(c.hasNext()).andReturn(Boolean.TRUE);
        expect(c.next()).andReturn(feature);
        expect(c.write()).andReturn(c);

        IExpectationSetters<FeatureWriteCursor> cursor =
                expect(vectorLayer.update(new VectorQuery().filter(new Id(new Literal(feature.id()))))).andReturn(c);
        if (!expectSuccess) {
            cursor.anyTimes();
        }

        expect(vectorLayer.update((VectorQuery) anyObject())).andReturn(FeatureWriteCursor.empty()).anyTimes();
        c.close();
        expectLastCall().once();

        expect(vectorLayer.read(new VectorQuery().filter((Filter) anyObject())))
                .andReturn(FeatureCursor.empty()).anyTimes();

        return this;
    }

    MockServer withSingleFeature() throws Exception {
        Feature f = createNiceMock(Feature.class);

        FeatureCursor c = createMock(FeatureCursor.class);
        expect(c.next()).andReturn(f).once();
        c.close();
        expectLastCall().once();

        expect(vectorLayer.read((VectorQuery) anyObject())).andReturn(c).once();

        return this;
    }

    MockServer withMoreDetails() throws Exception {
        Dataset active = vectorLayer == null ? tileLayer : vectorLayer;
        assert active != null : "expected a tile or vector layer";

        expect(active.name()).andReturn("emptylayer").anyTimes();
        expect(active.bounds()).andReturn(new Bounds(-180, 180, -90, 90)).anyTimes();
        expect(active.crs()).andReturn(Proj.EPSG_4326).anyTimes();
        Driver driver = createMock(Driver.class);
        expect(driver.name()).andReturn("mockDriver").anyTimes();
        expect(active.driver()).andReturn(driver).anyTimes();
        if (vectorLayer != null) {
            expect(vectorLayer.count((VectorQuery) anyObject())).andReturn(42L).anyTimes();
            Schema schema = createMock(Schema.class);
            Iterator<Field> fields = Iterators.forArray(
                    new Field("name", String.class)
            );
            expect(schema.iterator()).andReturn(fields).anyTimes();
            expect(vectorLayer.schema()).andReturn(schema).anyTimes();
        }
        return this;
    }

    MockServer replay() throws Exception {
        EasyMock.replay(mocks.toArray());
        return this;
    }

    void verify() {
        EasyMock.verify(mocks.toArray());
    }

    MockServer withWorkspace() throws Exception {
        workspace = createMock(Workspace.class);
        workspace.close();
        expectLastCall().atLeastOnce();

        expect(reg.get("foo", Workspace.class)).andReturn(workspace).once();
        expect(reg.get((String) anyObject(), (Class) anyObject())).andReturn(null).anyTimes();
        expect(server.getRegistry()).andReturn(reg).atLeastOnce();

        return this;
    }

    MockServer buildRegistry() throws Exception {
        expect(server.getRegistry()).andReturn(reg).anyTimes();
        expect(reg.query(new TypeOf<Handle<?>>(new Property("type"), Workspace.class))).andReturn(workspaces).anyTimes();
        expect(reg.query(new TypeOf<Handle<?>>(new Property("type"), Style.class))).andReturn(styles).anyTimes();
        expect(reg.list()).andReturn(Lists.newArrayList(Iterables.concat(workspaces, styles)));
        return this;
    }
    
    Handle<Dataset> createVectorDataset(String name, String title, Bounds env, Schema schema) throws Exception {
        CoordinateReferenceSystem crs = schema.geometry().crs();
        VectorDataset dataSet = createMock(VectorDataset.class);
        expect(dataSet.bounds()).andReturn(env).anyTimes();
        expect(dataSet.name()).andReturn(name).anyTimes();
        //expect(dataSet.title()).andReturn(title).anyTimes();
        expect(dataSet.crs()).andReturn(crs).anyTimes();
        expect(dataSet.schema()).andReturn(schema).anyTimes();
        Handle handle = createMock(Handle.class);
        expect(handle.type()).andReturn(VectorDataset.class).anyTimes();
        expect(handle.bounds()).andReturn(env).anyTimes();
        expect(handle.name()).andReturn(name).anyTimes();
        //expect(handle.title()).andReturn(title).anyTimes();
        expect(handle.crs()).andReturn(crs).anyTimes();
        expect(handle.resolve()).andReturn(dataSet).anyTimes();
        return handle;
    }

    Handle<Workspace> createWorkspace(String name, final Handle<Dataset>... ds) throws Exception {
        Handle<Workspace> handle = createMock(Handle.class);
        expect(handle.name()).andReturn(name).anyTimes();
        expect(handle.type()).andReturn(Workspace.class).anyTimes();
        Workspace ws = createMock(Workspace.class);
        workspaces.add(handle);
        expect(ws.list()).andReturn(Arrays.asList(ds)).anyTimes();
        expect(ws.get((String) anyObject())).andAnswer(new IAnswer<Dataset>() {

            @Override
            public Dataset answer() throws Throwable {
                String name = (String) EasyMock.getCurrentArguments()[0];
                for (Handle<Dataset> d: ds) {
                    if (name.equals(d.name())) return d.resolve();
                }
                return null;
            }
        }).anyTimes();
        expect(handle.resolve()).andReturn(ws).anyTimes();
        expect(reg.get(name, Workspace.class)).andReturn(ws).anyTimes();
        return handle;
    }

    Handle<Style> createStyle(String name) throws Exception {
        Handle<Style> handle = createMock(Handle.class);
        expect(handle.name()).andReturn(name).anyTimes();
        expect(handle.type()).andReturn(Style.class).anyTimes();

        Driver drv = createNiceMock(Driver.class);
        expect(drv.name()).andReturn("mock").anyTimes();
        expect(handle.driver()).andReturn(drv).anyTimes();

        Style style = createMock(Style.class);
        expect(handle.resolve()).andReturn(style).anyTimes();

        expect(reg.get(name, Style.class)).andReturn(style).anyTimes();

        styles.add(handle);
        return handle;
    }

    MockServer expectSchemaCreated() throws Exception {
        VectorDataset layer = createMock(VectorDataset.class);
        expect(workspace.get((String) anyObject())).andReturn(null).anyTimes();
        expect(workspace.create((Schema) anyObject())).andReturn(layer).once();
        return this;
    }

    MockServer withSingleDataWorkspace() throws Exception {
        withWorkspace();

        Driver driver = createMock(Driver.class);
        expect(driver.name()).andReturn("mockDriver");
        
        Handle<Dataset> dataSet = createMock(Handle.class);
        expect(dataSet.name()).andReturn("mockDataSet");

        expect(workspace.driver()).andReturn(driver);
        expect(workspace.list()).andReturn(Collections.singleton(dataSet));

        return this;
    }

    MockServer withTileLayer(boolean expectTileAccess) throws Exception {
        withWorkspace();

        tileLayer = createMock(TileDataset.class);
        if (expectTileAccess) {
            expect(tileLayer.read(1, 2, 3)).andReturn(new Tile(1,2,3,new byte[]{},"image/png")).once();
        }
        tileLayer.close();
        expectLastCall().once();

        TilePyramid tilePyramid = createMock(TilePyramid.class);
        expect(tilePyramid.bounds((Tile) anyObject())).andReturn(new Bounds(-42, 42, -42, 42)).anyTimes();
        expect(tileLayer.pyramid()).andReturn(tilePyramid).anyTimes();

        expect(workspace.get("bar")).andReturn(tileLayer).once();

        return this;
    }

    MockServer withWritableVectorLayer(Feature receiver) throws Exception {
        withVectorLayer();

        FeatureAppendCursor c = createMock(FeatureAppendCursor.class);
        expect(c.next()).andReturn(receiver);
        expect(c.write()).andReturn(c);

        expect(vectorLayer.append(new VectorQuery())).andReturn(c);
        c.close();
        expectLastCall().once();

        return this;
    }

    MockServer withMemoryVectorLayer() throws Exception {
        withWorkspace();

        Schema schema = new SchemaBuilder("memory")
                .field("name", String.class)
                .schema();
        memoryLayer = new MemVectorDataset(schema);
        memoryLayer.add(new ListFeature("42", schema, "foo"));
        expect(workspace.get("bar")).andReturn(memoryLayer).once();
        expect(workspace.get((String) anyObject())).andReturn(null).anyTimes();

        return this;
    }

    MockServer withPngRenderer() throws Exception {
        Renderer png = createMock(Renderer.class);
        png.init((View)anyObject(), (Map)anyObject());
        expectLastCall().anyTimes();
        png.render((OutputStream) anyObject());
        expectLastCall().anyTimes();
        png.close();
        expectLastCall().once();

        final RendererFactory rf = createMock(RendererFactory.class);
        expect(rf.getFormats()).andReturn(Arrays.asList("png","image/png")).anyTimes();
        expect(rf.create((View)anyObject(), (Map)anyObject())).andReturn(png).anyTimes();

        expect(rendererRegistry.list()).andAnswer(new IAnswer<Iterator<RendererFactory<?>>>() {
            @Override
            public Iterator<RendererFactory<?>> answer() throws Throwable {
                return (Iterator) Iterators.singletonIterator(rf);
            }
        }).anyTimes();
        expect(server.getRendererRegistry()).andReturn(rendererRegistry).anyTimes();
        return this;
    }
}
