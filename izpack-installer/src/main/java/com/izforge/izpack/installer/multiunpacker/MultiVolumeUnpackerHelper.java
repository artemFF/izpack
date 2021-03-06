package com.izforge.izpack.installer.multiunpacker;

import com.izforge.izpack.api.data.AutomatedInstallData;
import com.izforge.izpack.api.handler.AbstractUIProgressHandler;
import com.izforge.izpack.installer.base.InstallerFrame;
import com.izforge.izpack.installer.base.IzPanel;
import com.izforge.izpack.installer.unpacker.IMultiVolumeUnpackerHelper;
import com.izforge.izpack.util.Debug;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class MultiVolumeUnpackerHelper implements IMultiVolumeUnpackerHelper
{

    private AutomatedInstallData idata;

    private AbstractUIProgressHandler handler;

    public MultiVolumeUnpackerHelper()
    {

    }

    public File enterNextMediaMessage(String volumename, boolean lastcorrupt)
    {
        if (lastcorrupt)
        {
            Component parent = null;
            if ((this.handler != null) && (this.handler instanceof IzPanel))
            {
                parent = ((IzPanel) this.handler).getInstallerFrame();
            }
            JOptionPane.showMessageDialog(parent, idata.getLangpack()
                    .getString("nextmedia.corruptmedia"), idata.getLangpack()
                    .getString("nextmedia.corruptmedia.title"), JOptionPane.ERROR_MESSAGE);
        }
        Debug.trace("Enter next media: " + volumename);

        File nextvolume = new File(volumename);
        NextMediaDialog nextMediaDialog = null;

        while (!nextvolume.exists() || lastcorrupt)
        {
            if ((this.handler != null) && (this.handler instanceof IzPanel))
            {
                InstallerFrame installframe = ((IzPanel) this.handler).getInstallerFrame();
                nextMediaDialog = new NextMediaDialog(installframe, idata, volumename);
            }
            else
            {
                nextMediaDialog = new NextMediaDialog(null, idata, volumename);
            }
            nextMediaDialog.setVisible(true);
            String nextmediainput = nextMediaDialog.getNextMedia();
            if (nextmediainput != null)
            {
                nextvolume = new File(nextmediainput);
            }
            else
            {
                Debug.trace("Input from NextMediaDialog was null");
                nextvolume = new File(volumename);
            }
            // selection equal to last selected which was corrupt?
            if (!(volumename.equals(nextvolume.getAbsolutePath()) && lastcorrupt))
            {
                lastcorrupt = false;
            }
        }
        return nextvolume;
    }

    public File enterNextMediaMessage(String volumename)
    {
        return enterNextMediaMessage(volumename, false);
    }

    public void init(AutomatedInstallData idata, AbstractUIProgressHandler handler)
    {
        this.idata = idata;
        this.handler = handler;
    }
}
