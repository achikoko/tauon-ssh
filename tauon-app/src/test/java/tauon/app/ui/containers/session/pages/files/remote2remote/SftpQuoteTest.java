package tauon.app.ui.containers.session.pages.files.remote2remote;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SftpQuoteTest {

    @Test
    public void quotesAndEscapes() {
        assertEquals("\"a b\"", Remote2RemoteTransferDialog.sftpQuote("a b"));
        assertEquals("\"a\\\"b\"", Remote2RemoteTransferDialog.sftpQuote("a\"b"));
        assertEquals("\"back\\\\slash\"", Remote2RemoteTransferDialog.sftpQuote("back\\slash"));
        assertEquals("\"\\*\\?\\[x\\]\"", Remote2RemoteTransferDialog.sftpQuote("*?[x]"));
        // The shell does not expand the batch (<<'EOF'), and sftp does not expand these
        assertEquals("\"$(x) `y` it's\"", Remote2RemoteTransferDialog.sftpQuote("$(x) `y` it's"));
    }
}
