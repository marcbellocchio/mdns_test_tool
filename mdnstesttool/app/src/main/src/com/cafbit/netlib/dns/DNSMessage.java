/*
 * Copyright 2011 David Simmons
 * http://cafbit.com/entry/testing_multicast_support_on_android
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
package com.cafbit.netlib.dns;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * This class represents a single DNS message, and is capable
 * of parsing or constructing such a message.
 * 
 * see: http://www.ietf.org/rfc/rfc1035.txt
 * 
 * @author simmons
 */
public class DNSMessage {
    
    private static short nextMessageId = 0;

    private short messageId;
    private LinkedList<DNSQuestion> questions = new LinkedList<DNSQuestion>();
    private LinkedList<DNSAnswer> answers = new LinkedList<DNSAnswer>();
    private LinkedList<DNSAnswer> authorities = new LinkedList<DNSAnswer>();
    private LinkedList<DNSAnswer> addrecords = new LinkedList<DNSAnswer>();

    /**
     * Construct a DNS host query
     */
    public DNSMessage(String hostname) {
        messageId = 0x00; // nextMessageId++;
//        questions.add(new DNSQuestion(DNSQuestion.Type.ANY, hostname));
        questions.add(new DNSQuestion(DNSQuestion.Type.PTR, hostname));
    }
    
    /**
     * Parse the supplied packet as a DNS message.
     */
    public DNSMessage(byte[] packet) {
        parse(packet, 0, packet.length);
    }
    
    /**
     * Parse the supplied packet as a DNS message.
     */
    public DNSMessage(byte[] packet, int offset, int length) {
        parse(packet, offset, length);
    }
    
    public int length() {
        int length = 12; // header length
        for (DNSQuestion q : questions) {
            length += q.length();
        }
        for (DNSAnswer a : answers) {
            length += a.length();
        }
        for (DNSAnswer t : authorities) {
            length += t.length();
        }
        for (DNSAnswer r : addrecords) {
            length += r.length();
        }
        return length;
    }
    
    public byte[] serialize() {
        DNSBuffer buffer = new DNSBuffer(length());
        
        // header
        buffer.writeShort(messageId);
        buffer.writeShort(0); // flags
        buffer.writeShort(questions.size());    // qdcount
        buffer.writeShort(answers.size());      // ancount
        buffer.writeShort(authorities.size());  // nscount
        buffer.writeShort(addrecords.size());   // arcount
        
        // questions
        for (DNSQuestion question : questions) {
            question.serialize(buffer);
        }
        
        // answers
        for (DNSAnswer answer : answers) {
            answer.serialize(buffer);
        }

        // authority records
        for (DNSAnswer answer : authorities) {
            answer.serialize(buffer);
        }

        // additional records
        for (DNSAnswer answer : addrecords) {
            answer.serialize(buffer);
        }

        return buffer.bytes;
    }


    /**
    *       Id--- flags Quest Answ- Auth  AdRec
    * 0000: 00 00 84 00 00 00 00 01 00 00 00 03 09 5F 73 64 ............._sd
    * 0010: 74 76 77 63 61 6D 04 5F 74 63 70 05 6C 6F 63 61 tvwcam._tcp.loca
    * 0020: 6C 00 00 0C 80 01 00 00 11 94 00 0B 08 61 32 34 l............a24
    * 0030: 37 37 37 34 62 C0 0C C0 2C 00 10 80 01 00 00 11 7774b...,.......
    * 0040: 94 00 10 0F 53 4D 41 52 54 43 41 4D 5F 4B 61 72 ....SMARTCAM_Kar
    * 0050: 69 6E 65 C0 2C 00 21 80 01 00 00 00 78 00 11 00 ine.,.!.....x...
    * 0060: 00 00 00 09 70 08 61 32 34 37 37 37 34 62 C0 1B ....p.a247774b..
    * 0070: 08 61 32 34 37 37 37 34 62 C0 1B 00 01 80 01 00 .a247774b.......
    * 0080: 00 00 78 00 04 C0 A8 00 77                      ..x.....w
     *
     * Id = 0x00
     * Flas = 0x84oo
     * Questions = 0
     * Answers = 1
     * Authority records = 0
     * Additional Records = 3
     * Questions = ''
     * Answer = '_sdtvwcam._tcp.local.'
     * Type = 0x000C (PTR)
     * Class = 0x8001
     *
     * Each record is composed by name, type, class, ttl, length, data
     * name is a string that can be compressed: the compression tag is 0xC0 and is
     * followed by the absolute offset of corresponding text
     * each text starts with length followed by characters
    **/
    private void parse(byte[] packet, int offset, int length) {
        DNSBuffer buffer = new DNSBuffer(packet, offset, length);
        
        // header
        messageId = buffer.readShort();     // Message Identifier
        buffer.readShort();                 // Flags
        int qdcount = buffer.readShort();   // Number of Queries
        int ancount = buffer.readShort();   // Number of Answers
        int arcount = buffer.readShort();   // Number of Authority Records
        int adcount = buffer.readShort();   // Number of Additional Records
        
        // Read all the questions
        questions.clear();
        for (int i=0; i<qdcount; i++) {
            questions.add(new DNSQuestion(buffer));
        }
        
        // Read all the answers
        answers.clear();
        for (int i=0; i<ancount; i++) {
            answers.add(new DNSAnswer(buffer));
        }

        // Read all the authority record
        authorities.clear();
        for (int i=0; i<arcount; i++) {
            authorities.add(new DNSAnswer(buffer));
        }

        // Read all the additional records
        addrecords.clear();
        for (int i=0; i<adcount; i++) {
            addrecords.add(new DNSAnswer(buffer));
        }
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // questions
        for (DNSQuestion q : questions) {
            sb.append("\nQuestion: "+q.toString()+"\n");
        }
        
        // group answers by name
        SortedMap<String,List<DNSAnswer>> answersByName = new TreeMap<String,List<DNSAnswer>>();

        for (DNSAnswer a : answers) {
            List<DNSAnswer> list;
            if (answersByName.containsKey(a.name)) {
                list = answersByName.get(a.name);
            } else {
                list = new LinkedList<DNSAnswer>();
                answersByName.put(a.name, list);
            }
            list.add(a);
        }

        for (DNSAnswer a : authorities) {
            List<DNSAnswer> list;
            if (answersByName.containsKey(a.name)) {
                list = answersByName.get(a.name);
            } else {
                list = new LinkedList<DNSAnswer>();
                answersByName.put(a.name, list);
            }
            list.add(a);
        }

        for (DNSAnswer a : addrecords) {
            List<DNSAnswer> list;
            if (answersByName.containsKey(a.name)) {
                list = answersByName.get(a.name);
            } else {
                list = new LinkedList<DNSAnswer>();
                answersByName.put(a.name, list);
            }
            list.add(a);
        }

        for (Map.Entry<String, List<DNSAnswer>> entry : answersByName.entrySet()) {
            sb.append(entry.getKey()+"\n");
            for (DNSAnswer a : entry.getValue()){
                sb.append("  "+a.type.toString()+" "+a.getRdataString()+"\n");
            }
        }

        return sb.toString();
    }



/*
    Example
    {
        "netservice" : "0x10ad11d60",
        "protocol" : "tcp",
        "domain" : "local",
        "port" : "2416",
        "hostname" : "myapp.local.",
        "morecoming" : "0",
        "qualifiedname" : "Coolmedia Service._http._tcp.local.",
        "addresses" : ["192.168.2.2"],
        "type" : "_http._tcp.local.",
        "application" : "http",
        "name" : "Coolmedia Service"
    }
*/
    public JSONObject toJSON() {

        Log.d("DNSMessage","toJSON: ENTER");

        JSONObject json = new JSONObject();
        JSONArray urls = new JSONArray();
        JSONArray addresses = new JSONArray();
        int port=0, elements=0;

        LinkedList<DNSAnswer> list = new LinkedList<DNSAnswer>();

        // merge the lists of information
        for (DNSAnswer a : answers) {
            list.add(a);
            elements++;
        }
        for (DNSAnswer a : authorities) {
            list.add(a);
            elements++;
        }
        for (DNSAnswer a : addrecords) {
            list.add(a);
            elements++;
        }

        Log.v("DNSMessage",String.format("Total elements %d",elements));

        // scan the merged list and creates the structure
        try {
            for (DNSAnswer a : list) {
                if(a.type == DNSComponent.Type.SRV) {
                    Log.v("DNSMessage",String.format("Parsing SRV, name:%s, host %s, port %d",a.name,a.getRdataString(),a.port));
                    // rdata=a247774b.local 2416
                    json.put("hostname",a.getRdataString());
                    json.put("port", a.port);
                    port = a.port;
                } else if(a.type == DNSComponent.Type.A || a.type == DNSComponent.Type.AAAA) {
                    Log.v("DNSMessage",String.format("Parsing A, name:%s, data %s",a.name,a.getRdataString()));
                    // rdata=/192.168.0.119
                    addresses.put(a.getRdataString());
                } else if(a.type == DNSComponent.Type.TXT) {
                    Log.v("DNSMessage",String.format("Parsing TXT, name:%s, data %s",a.name,a.getRdataString()));
                    // rdata=SMARTCAM_Karine
                    json.put("nickname",a.getRdataString());
                } else if(a.type == DNSComponent.Type.PTR) {
                    Log.v("DNSMessage",String.format("Parsing PTR, name:%s, data %s",a.name,a.getRdataString()));
                    // rdata=a247774b._sdtvwcam._tcp.local
                    String data = a.getRdataString();
                    String[] words = data.split("\\.");
                    json.put("qualifiedname",data);
                    json.put("name",words[0]);
                    json.put("service",words[1].substring(1));
                    json.put("protocol",words[2].substring(1));
                    json.put("domain",words[3]);
                    json.put("type",String.format("%s.%s.%s",words[1],words[2],words[3]));
                }
            }
            // creates the urls using addresses and port
            for(int i=0; i < addresses.length();i++) {
                String address = addresses.getString(i);
                urls.put("http://"+address+":"+port+"/");
                // urls.put(String.format("http:/%s:%u/",address,port));
            }
            json.put("addresses", addresses);
            json.put("urls", urls);
        } catch (JSONException e) {
            Log.e("ZeroConfig", "toJson: cannot handle json");
            // e.printStackTrace();
            return null;
        }

        Log.d("DNSMessage","toJSON: EXIT "+json.toString());

        return json;
    }
}
