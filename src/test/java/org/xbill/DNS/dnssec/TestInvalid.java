// SPDX-License-Identifier: BSD-3-Clause
package org.xbill.DNS.dnssec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DNSSEC.Algorithm;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.RRSIGRecord;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

class TestInvalid extends TestBase {
  @ParameterizedTest(name = "testInvalid_{arguments}")
  @ValueSource(
      strings = {
        "unknownalgorithm.dnssec",
        "sigexpired.dnssec",
        "bogussig.dnssec",
        "unknownalgorithm.nsec3",
        "sigexpired.nsec3",
        "bogussig.nsec3"
      })
  @AlwaysOffline
  void testInvalid(String param) throws IOException {
    Message response = resolver.send(createMessage(param + ".tjeb.nl./A"));
    assertFalse(response.getHeader().getFlag(Flags.AD), "AD flag must not be set");
    assertEquals(Rcode.SERVFAIL, response.getRcode());
    assertEquals("validate.bogus.badkey:" + param + ".tjeb.nl.:failed.ds", getReason(response));
  }

  @Test
  @AlwaysOffline
  void testSignedBelowUnsignedBelowSigned() throws IOException {
    Message response = resolver.send(createMessage("ok.nods.ok.dnssec.tjeb.nl./A"));
    assertFalse(response.getHeader().getFlag(Flags.AD), "AD flag must not be set");
    assertEquals(Rcode.NOERROR, response.getRcode());
    assertFalse(isEmptyAnswer(response));
    assertEquals("insecure.ds.nsec", getReason(response));
  }

  @Test
  @AlwaysOffline
  void testSignedBelowUnsignedBelowSignedNsec3() throws IOException {
    Message response = resolver.send(createMessage("ok.nods.ok.Nsec3.tjeb.nl./A"));
    assertFalse(response.getHeader().getFlag(Flags.AD), "AD flag must not be set");
    assertEquals(Rcode.NOERROR, response.getRcode());
    assertFalse(isEmptyAnswer(response));
    assertEquals("insecure.ds.nsec3", getReason(response));
  }

  @Test
  void testUnsignedThatMustBeSigned() throws IOException {
    Name query = Name.fromString("www.ingotronic.ch.");

    // prepare a faked, unsigned response message that must have a signature
    // to be valid
    Message message = new Message();
    message.addRecord(Record.newRecord(query, Type.A, DClass.IN), Section.QUESTION);
    message.addRecord(
        new ARecord(query, Type.A, DClass.IN, InetAddress.getByName(localhost)), Section.ANSWER);
    add("www.ingotronic.ch./A", message);

    Message response = resolver.send(createMessage("www.ingotronic.ch./A"));
    assertFalse(response.getHeader().getFlag(Flags.AD), "AD flag must not be set");
    assertEquals(Rcode.SERVFAIL, response.getRcode());
    assertEquals("validate.bogus.missingsig", getReason(response));
  }

  @Test
  void testModifiedSignature() throws IOException {
    Name query = Name.fromString("www.ingotronic.ch.");

    // prepare a faked, unsigned response message that must have a signature
    // to be valid
    Message message = new Message();
    message.addRecord(Record.newRecord(query, Type.A, DClass.IN), Section.QUESTION);
    message.addRecord(
        new ARecord(query, Type.A, DClass.IN, InetAddress.getByName(localhost)), Section.ANSWER);
    Instant now = Instant.now();
    message.addRecord(
        new RRSIGRecord(
            query,
            DClass.IN,
            0,
            Type.A,
            Algorithm.RSASHA256,
            5,
            now.plusSeconds(5),
            now.minusSeconds(5),
            1234,
            Name.fromString("ingotronic.ch."),
            new byte[] {1, 2, 3}),
        Section.ANSWER);
    add("www.ingotronic.ch./A", message);

    Message response = resolver.send(createMessage("www.ingotronic.ch./A"));
    assertFalse(response.getHeader().getFlag(Flags.AD), "AD flag must not be set");
    assertEquals(Rcode.SERVFAIL, response.getRcode());
    assertTrue(getReason(response).startsWith("failed.answer.positive:{ www.ingotronic.ch."));
  }

  @Test
  void testReturnServfailIfIntermediateQueryFails() throws IOException {
    Message message = new Message();
    message.getHeader().setRcode(Rcode.NOTAUTH);
    message.addRecord(
        Record.newRecord(Name.fromString("ch."), Type.DS, DClass.IN), Section.QUESTION);
    add("ch./DS", message);

    Message response = resolver.send(createMessage("www.ingotronic.ch./A"));
    assertFalse(response.getHeader().getFlag(Flags.AD), "AD flag must not be set");
    // rfc4035#section-5.5
    assertEquals(Rcode.SERVFAIL, response.getRcode());
    assertEquals("validate.bogus.badkey:ch.:failed.ds.nonsec:ch.", getReason(response));
  }

  @Test
  void testReturnOriginalRcodeIfPrimaryQueryFails() throws IOException {
    Message message = new Message();
    message.getHeader().setRcode(Rcode.REFUSED);
    message.addRecord(
        Record.newRecord(Name.fromString("www.ingotronic.ch."), Type.A, DClass.IN),
        Section.QUESTION);
    add("www.ingotronic.ch./A", message);

    Message response = resolver.send(createMessage("www.ingotronic.ch./A"));
    assertFalse(response.getHeader().getFlag(Flags.AD), "AD flag must not be set");
    // rfc4035#section-5.5
    assertEquals(Rcode.REFUSED, response.getRcode());
    assertEquals("failed.nodata", getReason(response));
  }
}
