/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.plugin.remotese.transport.json;

import com.google.gson.*;
import org.eclipse.keyple.util.ByteArrayUtils;

import java.lang.reflect.Type;

class GsonByteBufferTypeAdapter
        implements JsonDeserializer<byte[]>, JsonSerializer<byte[]> {

    @Override
    public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(ByteArrayUtils.toHex(src));
    }

    @Override
    public byte[] deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {
        return ByteArrayUtils.fromHex(json.getAsString());
    }


}
