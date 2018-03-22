/**
 * Copyright 2017 Alex Yanchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.droidparts.inner.converter;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import org.json.JSONObject;

import org.droidparts.inner.TypeHelper;

public class UriConverter extends Converter<Uri> {

	@Override
	public boolean canHandle(Class<?> cls) {
		return TypeHelper.isUri(cls);
	}

	@Override
	public String getDBColumnType() {
		return TEXT;
	}

	@Override
	public <G1, G2> void putToJSON(Class<Uri> valType, Class<G1> genericArg1, Class<G2> genericArg2, JSONObject obj,
	                               String key, Uri val) throws Exception {
		obj.put(key, val.toString());
	}

	@Override
	protected <G1, G2> Uri parseFromString(Class<Uri> valType, Class<G1> genericArg1, Class<G2> genericArg2,
	                                       String str) {
		return Uri.parse(str);
	}

	@Override
	public <G1, G2> void putToContentValues(Class<Uri> valueType, Class<G1> genericArg1, Class<G2> genericArg2,
	                                        ContentValues cv, String key, Uri val) {
		cv.put(key, val.toString());
	}

	@Override
	public <G1, G2> Uri readFromCursor(Class<Uri> valType, Class<G1> genericArg1, Class<G2> genericArg2, Cursor cursor,
	                                   int columnIndex) {
		return Uri.parse(cursor.getString(columnIndex));
	}

}