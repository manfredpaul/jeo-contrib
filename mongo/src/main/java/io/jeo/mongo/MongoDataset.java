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
package io.jeo.mongo;

import java.io.IOException;
import java.util.Map;

import io.jeo.data.Driver;
import io.jeo.geom.Bounds;
import io.jeo.vector.FeatureAppendCursor;
import io.jeo.vector.FeatureCursor;
import io.jeo.vector.FeatureWriteCursor;
import io.jeo.vector.VectorQuery;
import io.jeo.vector.VectorQueryPlan;
import io.jeo.vector.VectorDataset;
import io.jeo.vector.Schema;
import io.jeo.vector.SchemaBuilder;
import io.jeo.filter.Filters;
import io.jeo.proj.Proj;
import io.jeo.util.Key;
import org.osgeo.proj4j.CoordinateReferenceSystem;

import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class MongoDataset implements VectorDataset {

    MongoWorkspace mongo;
    DBCollection dbcol;
    MongoMapper mapper;
    Schema schema;

    MongoDataset(DBCollection dbcol, MongoWorkspace mongo) {
        this.dbcol = dbcol;
        this.mongo = mongo;
        this.schema = new SchemaBuilder(dbcol.getName()).schema();
    }

    public MongoMapper getMapper() {
        return mapper;
    }

    public void setMapper(MongoMapper mapper) {
        this.mapper = mapper;
    }

    public MongoMapper mapper() {
        return this.mapper != null ? mapper : mongo.getMapper();
    }

    public DBCollection getCollection() {
        return dbcol;
    }

    @Override
    public Driver<?> driver() {
        return mongo.driver();
    }

    @Override
    public Map<Key<?>, Object> driverOptions() {
        return mongo.driverOptions();
    }

    @Override
    public String name() {
        return dbcol.getName();
    }

    @Override
    public CoordinateReferenceSystem crs() {
        return Proj.EPSG_4326;
    }

    @Override
    public Bounds bounds() throws IOException {
        return mapper().bbox(dbcol, this);
    }

    @Override
    public Schema schema() throws IOException {
        return schema;
    }

    @Override
    public long count(VectorQuery q) throws IOException {
        if (q.isAll()) {
            return q.adjustCount(dbcol.count());
        }

        VectorQueryPlan qp = new VectorQueryPlan(q);

        if (!Filters.isTrueOrNull(q.filter())) {
            // TODO: transform natively to filter 
            // we can't optimize
            return qp.apply(read(q)).count();
        }

        long count = 
            q.bounds() != null ? dbcol.count(encodeBboxQuery(q.bounds())) : dbcol.count();

        return q.adjustCount(count);
    }

    @Override
    public FeatureCursor read(VectorQuery q) throws IOException {
        VectorQueryPlan qp = new VectorQueryPlan(q);

        //TODO: sorting
        DBCursor dbCursor = !Bounds.isNull(q.bounds()) ?
            dbcol.find(encodeBboxQuery(q.bounds())) : dbcol.find();
        qp.bounded();

        Integer offset = q.offset();
        if (offset != null) {
            dbCursor.skip(offset);
            qp.offsetted();
        }

        Integer limit = q.limit();
        if (limit != null) {
            dbCursor.limit(limit);
            qp.limited();
        }

        return qp.apply(new MongoCursor(dbCursor, this));
    }

    DBObject encodeBboxQuery(Bounds bbox) {
        return mapper().query(bbox, this);
    }

    @Override
    public FeatureWriteCursor update(VectorQuery q) throws IOException {
        return new MongoUpdateCursor(read(q), this);
    }

    @Override
    public FeatureAppendCursor append(VectorQuery q) throws IOException {
        return new MongoAppendCursor(this);
    }

    @Override
    public void close() {
    }
}
