# FSEvents Parser Python Extraction Plugin
# ------------------------------------------------------
# This script is a heavily modified version of the fsevents script found here:
# https://github.com/nicoleibrahim/FSEventsParser
# Changes were made to use the existing script as Hansken Extraction Plugin.

import binascii
import contextlib
import gzip
import os
import re
import struct
from time import (gmtime, strftime)

from hansken.abstract_trace import AbstractTrace
from hansken_extraction_plugin.api.data_context import DataContext
from hansken_extraction_plugin.api.extraction_plugin import ExtractionPlugin
from hansken_extraction_plugin.api.plugin_info import Author, MaturityLevel, PluginId, PluginInfo
from logbook import Logger

log = Logger(__name__)


EVENTMASK = {
    0x00000000: 'None;',
    0x00000001: 'FolderEvent;',
    0x00000002: 'Mount;',
    0x00000004: 'Unmount;',
    0x00000020: 'EndOfTransaction;',
    0x00000800: 'LastHardLinkRemoved;',
    0x00001000: 'HardLink;',
    0x00004000: 'SymbolicLink;',
    0x00008000: 'FileEvent;',
    0x00010000: 'PermissionChange;',
    0x00020000: 'ExtendedAttrModified;',
    0x00040000: 'ExtendedAttrRemoved;',
    0x00100000: 'DocumentRevisioning;',
    0x00400000: 'ItemCloned;',  # macOS HighSierra
    0x01000000: 'Created;',
    0x02000000: 'Removed;',
    0x04000000: 'InodeMetaMod;',
    0x08000000: 'Renamed;',
    0x10000000: 'Modified;',
    0x20000000: 'Exchange;',
    0x40000000: 'FinderInfoMod;',
    0x80000000: 'FolderCreated;',
    0x00000008: 'NOT_USED-0x00000008;',
    0x00000010: 'NOT_USED-0x00000010;',
    0x00000040: 'NOT_USED-0x00000040;',
    0x00000080: 'NOT_USED-0x00000080;',
    0x00000100: 'NOT_USED-0x00000100;',
    0x00000200: 'NOT_USED-0x00000200;',
    0x00000400: 'NOT_USED-0x00000400;',
    0x00002000: 'NOT_USED-0x00002000;',
    0x00080000: 'NOT_USED-0x00080000;',
    0x00200000: 'NOT_USED-0x00200000;',
    0x00800000: 'NOT_USED-0x00800000;'
}


def enumerate_flags(flag, f_map):
    """
    Iterate through record flag mappings and enumerate.
    """
    # Reset string based flags to null
    f_type = ''
    f_flag = ''
    # Iterate through flags
    for i in f_map:
        if i & flag:
            if f_map[i] == 'FolderEvent;' or \
                    f_map[i] == 'FileEvent;' or \
                    f_map[i] == 'SymbolicLink;' or \
                    f_map[i] == 'HardLink;':
                f_type = ''.join([f_type, f_map[i]])
            else:
                f_flag = ''.join([f_flag, f_map[i]])
    return f_type, f_flag


class FSEventHandler:
    """
    FSEventHandler iterates through and parses fsevents.
    """

    def __init__(self):
        """
        """

        self.files = []
        self.pages = []
        self.src_fullpath = ''
        self.dls_version = 0

        # Initialize statistic counters
        self.all_records_count = 0
        self.all_files_count = 0
        self.parsed_file_count = 0
        self.error_file_count = 0

        # Begin FSEvent processing

        log.debug('\n[STARTED] {} UTC Parsing files.'.format(strftime("%m/%d/%Y %H:%M:%S", gmtime())))

        # Uses file mod dates to generate time ranges by default unless
        # files are carved or mod dates lost due to exporting
        self.use_file_mod_dates = True

    @contextlib.contextmanager
    def skip_gzip_check(self):
        """
        Context manager that replaces gzip.GzipFile._read_eof with a no-op.
        This is useful when decompressing partial files, something that won't
        work if GzipFile does it's checksum comparison.
        stackoverflow.com/questions/1732709/unzipping-part-of-a-gz-file-using-python/18602286
        """
        _read_eof = gzip._GzipReader._read_eof
        gzip.GzipFile._read_eof = lambda *args, **kwargs: None
        yield
        gzip.GzipFile._read_eof = _read_eof

    def parse_event(self, trace):
        log.debug("Processing trace ", trace.get('name'))
        # update trace to be an eventlog
        trace.update(
            'eventLog.application', 'fseventsd'
        )

        # write data from trace to tmp file
        self.src_fullpath = os.path.join('/tmp', trace.get('name'))
        with open(self.src_fullpath, 'wb') as file, trace.open() as trace_data:
            file.write(trace_data.read())

        # Name of source fsevent file
        self.src_filename = trace.get('name')
        # UTC mod date of source fsevent file
        self.m_time = trace.get('file.modifiedOn')

        # Regex to match against source fsevent log filename
        regexp = re.compile(r'^.*[\][0-9a-fA-F]{16}$')

        # Test to see if fsevent file name matches naming standard
        # if not, assume this is a carved gzip
        if len(self.src_filename) == 16 and regexp.search(self.src_filename) is not None:
            c_last_wd = int(self.src_filename, 16)
            self.time_range_src_mod = 0, c_last_wd, 0, self.m_time
            self.is_carved_gzip = False
        else:
            self.is_carved_gzip = True

        # Attempt to decompress the fsevent archive
        try:
            with self.skip_gzip_check():
                self.files = gzip.GzipFile(self.src_fullpath)
                buf = self.files.read()

        except Exception as exp:
            log.warning(
                "%s\tError: Error while decompressing FSEvents file.%s\n" % (
                    self.src_filename,
                    str(exp)
                )
            )
            self.error_file_count += 1
            return

        # If decompress is success, check for DLS headers in the current file
        dls_chk = FSEventHandler.dls_header_search(self, buf, self.src_fullpath)

        # If check for DLS returns false, write information to log
        if dls_chk is False:
            log.warning(f'{trace.get("name")}\tInfo: DLS Header Check Failed. Unable to find a '
                  'DLS header. Unable to parse File.\n')
            # Continue to the next file in the fsevents directory
            self.error_file_count += 1
            return

        self.parsed_file_count += 1

        # Accounts for fsevent files that get flushed to disk
        # at the same time. Usually the result of a shutdown
        # or unmount
        if not self.is_carved_gzip and self.use_file_mod_dates:
            prev_mod_date = self.m_time
            prev_last_wd = int(self.src_filename, 16)

        # If DLSs were found, pass the decompressed file to be parsed
        FSEventHandler.parse(self, buf, trace)

    def dls_header_search(self, buf, f_name):
        """
        Search within the unzipped file
        for all occurrences of the DLS magic header.
        There can be more than one DLS header in an fsevents file.
        The start and end offsets are stored and used for parsing
        the records contained within each DLS page.
        """
        self.file_size = len(buf)
        self.my_dls = []

        raw_file = buf
        dls_count = 0
        start_offset = 0
        end_offset = 0

        while end_offset != self.file_size:
            try:
                start_offset = end_offset
                page_len = struct.unpack("<I", raw_file[start_offset + 8:start_offset + 12])[0]
                end_offset = start_offset + page_len

                if raw_file[start_offset:start_offset + 4] == b'1SLD' or raw_file[start_offset:start_offset + 4] == b'2SLD':
                    self.my_dls.append({'Start Offset': start_offset, 'End Offset': end_offset})
                    dls_count += 1
                else:
                    log.warning(f"{f_name}: Error in length of page when finding page headers.")
                    break
            except:
                log.warning(f"{f_name}: Error in length of page when finding page headers.")
                break

        if dls_count == 0:
            # Return false to caller so that the next file will be searched
            return False
        else:
            # Return true so that the DLSs found can be parsed
            return True

    def parse(self, buf, trace):
        """
        Parse the decompressed fsevent log. First
        finding other dates, then iterating through
        eash DLS page found. Then parse records within
        each page.
        """
        # Initialize variables
        pg_count = 0

        # Call the date finder for current fsevent file
        FSEventHandler.find_date(self, buf)
        self.valid_record_check = True

        # Iterate through DLS pages found in current fsevent file
        for i in self.my_dls:
            # Assign current DLS offsets
            start_offset = self.my_dls[pg_count]['Start Offset']
            end_offset = self.my_dls[pg_count]['End Offset']

            # Extract the raw DLS page from the fsevents file
            raw_page = buf[start_offset:end_offset]

            self.page_offset = start_offset

            # Reverse byte stream to match byte order little-endian
            m_dls_chk = raw_page[0:4]
            # Assign DLS version based off magic header in page
            if m_dls_chk == b"1SLD":
                self.dls_version = 1
            elif m_dls_chk == b"2SLD":
                self.dls_version = 2
            else:
                log.debug(f"{self.src_filename}: Unknown DLS Version.")
                break

            # Pass the raw page + a start offset to find records within page
            FSEventHandler.find_page_records(
                self,
                raw_page,
                start_offset,
                trace
            )
            # Increment the DLS page count by 1
            pg_count += 1


    def find_date(self, raw_file):
        """
        Search within current file for names of log files that are created
        that store the date as a part of its naming
        standard.
        """
        # Reset variables
        self.time_range = []

        # Add previous file's mod timestamp, wd and current file's timestamp, wd
        # to time range
        if not self.is_carved_gzip and self.use_file_mod_dates:
            c_time_1 = str(self.time_range_src_mod[2])[:10].replace("-", ".")
            c_time_2 = str(self.time_range_src_mod[3])[:10].replace("-", ".")

            self.time_range.append([self.time_range_src_mod[0], c_time_1])
            self.time_range.append([self.time_range_src_mod[1], c_time_2])

        # Regex's for logs with dates in name
        regex_1 = ("private/var/log/asl/[\x30-\x39]{4}[.][\x30-\x39]{2}" +
                   "[.][\x30-\x39]{2}[.][\x30-\x7a]{2,8}[.]asl")
        regex_2 = ("mobile/Library/Logs/CrashReporter/DiagnosticLogs/security[.]log" +
                   "[.][\x30-\x39]{8}T[\x30-\x39]{6}Z")
        regex_3 = ("private/var/log/asl/Logs/aslmanager[.][\x30-\x39]{8}T[\x30-\x39]" +
                   "{6}[-][\x30-\x39]{2}")
        regex_4 = ("private/var/log/DiagnosticMessages/[\x30-\x39]{4}[.][\x30-\x39]{2}" +
                   "[.][\x30-\x39]{2}[.]asl")
        regex_5 = ("private/var/log/com[.]apple[.]clouddocs[.]asl/[\x30-\x39]{4}[.]" +
                   "[\x30-\x39]{2}[.][\x30-\x39]{2}[.]asl")
        regex_6 = ("private/var/log/powermanagement/[\x30-\x39]{4}[.][\x30-\x39]{2}[.]" +
                   "[\x30-\x39]{2}[.]asl")
        regex_7 = ("private/var/log/asl/AUX[.][\x30-\x39]{4}[.][\x30-\x39]{2}[.]" +
                   "[\x30-\x39]{2}/[0-9]{9}")
        regex_8 = "private/var/audit/[\x30-\x39]{14}[.]not_terminated"

        # Regex that matches only events with created flag
        flag_regex = ("[\x00-\xFF]{9}[\x01|\x11|\x21|\x31|\x41|\x51|\x61|\x05|\x15|" +
                      "\x25|\x35|\x45|\x55|\x65]")

        # Concatenating date, flag matching regexes
        # Also grabs working descriptor for record
        m_regex = "(" + regex_1 + "|" + regex_2 + "|" + regex_3 + "|" + regex_4 + "|" + regex_5
        m_regex = m_regex + "|" + regex_6 + "|" + regex_7 + "|" + regex_8 + ")" + flag_regex

        # Start searching within fsevent file for events that match dates regex
        # As the length of each log location is different, create if statements for each
        # so that the date can be pulled from the correct location within the fullpath

        #decode to latin as it preveserves all lengths (1 byte == 1 character)
        raw_file_ascii = raw_file.decode("latin_1")
        for match in re.finditer(m_regex, raw_file_ascii):
            if raw_file_ascii[match.regs[0][0]:match.regs[0][0] + 35] == "private/var/log/asl/Logs/aslmanager":
                # Clear timestamp temp variable
                t_temp = ''
                # t_start uses the start offset of the match
                t_start = match.regs[0][0] + 36
                # The date is 8 chars long in the format of yyyymmdd
                t_end = t_start + 8
                # Strip the date from the fsevent file
                t_temp = raw_file_ascii[t_start:t_end]
                # Format the date
                t_temp = t_temp[:4] + "." + t_temp[4:6] + "." + t_temp[6:8]
                wd_temp = struct.unpack("<Q", raw_file[match.regs[0][1] - 9:match.regs[0][1] - 1])[0]
            elif raw_file_ascii[match.regs[0][0]:match.regs[0][0] + 23] == "private/var/log/asl/AUX":
                # Clear timestamp temp variable
                t_temp = ''
                # t_start uses the start offset of the match
                t_start = match.regs[0][0] + 24
                # The date is 10 chars long in the format of yyyy.mm.dd
                t_end = t_start + 10
                # Strip the date from the fsevent file
                t_temp = raw_file_ascii[t_start:t_end]
                wd_temp = struct.unpack("<Q", raw_file[match.regs[0][1] - 9:match.regs[0][1] - 1])[0]
            elif raw_file_ascii[match.regs[0][0]:match.regs[0][0] + 19] == "private/var/log/asl":
                # Clear timestamp temp variable
                t_temp = ''
                # t_start uses the start offset of the match
                t_start = match.regs[0][0] + 20
                # The date is 10 chars long in the format of yyyy.mm.dd
                t_end = t_start + 10
                # Strip the date from the fsevent file
                t_temp = raw_file_ascii[t_start:t_end]
                wd_temp = struct.unpack("<Q", raw_file[match.regs[0][1] - 9:match.regs[0][1] - 1])[0]
            elif raw_file_ascii[match.regs[0][0]:match.regs[0][0] + 4] == "mobi":
                # Clear timestamp temp variable
                t_temp = ''
                # t_start uses the start offset of the match
                t_start = match.regs[0][0] + 62
                # The date is 8 chars long in the format of yyyymmdd
                t_end = t_start + 8
                # Strip the date from the fsevent file
                t_temp = raw_file_ascii[t_start:t_end]
                # Format the date
                t_temp = t_temp[:4] + "." + t_temp[4:6] + "." + t_temp[6:8]
                wd_temp = struct.unpack("<Q", raw_file[match.regs[0][1] - 9:match.regs[0][1] - 1])[0]
            elif raw_file_ascii[match.regs[0][0]:match.regs[0][0] + 34] == "private/var/log/DiagnosticMessages":
                # Clear timestamp temp variable
                t_temp = ''
                # t_start uses the start offset of the match
                t_start = match.regs[0][0] + 35
                # The date is 10 chars long in the format of yyyy.mm.dd
                t_end = t_start + 10
                # Strip the date from the fsevent file
                t_temp = raw_file_ascii[t_start:t_end]
                wd_temp = struct.unpack("<Q", raw_file[match.regs[0][1] - 9:match.regs[0][1] - 1])[0]
            elif raw_file_ascii[match.regs[0][0]:match.regs[0][0] + 39] == "private/var/log/com.apple.clouddocs.asl":
                # Clear timestamp temp variable
                t_temp = ''
                # t_start uses the start offset of the match
                t_start = match.regs[0][0] + 40
                # The date is 10 chars long in the format of yyyy.mm.dd
                t_end = t_start + 10
                # Strip the date from the fsevent file
                t_temp = raw_file_ascii[t_start:t_end]
                wd_temp = struct.unpack("<Q", raw_file[match.regs[0][1] - 9:match.regs[0][1] - 1])[0]
            elif raw_file_ascii[match.regs[0][0]:match.regs[0][0] + 31] == "private/var/log/powermanagement":
                # Clear timestamp temp variable
                t_temp = ''
                # t_start uses the start offset of the match
                t_start = match.regs[0][0] + 32
                # The date is 10 chars long in the format of yyyy.mm.dd
                t_end = t_start + 10
                # Strip the date from the fsevent file
                t_temp = raw_file_ascii[t_start:t_end]
                wd_temp = struct.unpack("<Q", raw_file[match.regs[0][1] - 9:match.regs[0][1] - 1])[0]
            elif raw_file_ascii[match.regs[0][0]:match.regs[0][0] + 17] == "private/var/audit":
                # Clear timestamp temp variable
                t_temp = ''
                # t_start uses the start offset of the match
                t_start = match.regs[0][0] + 18
                # The date is 8 chars long in the format of yyyymmdd
                t_end = t_start + 8
                # Strip the date from the fsevent file
                t_temp = raw_file_ascii[t_start:t_end]
                # Format the date
                t_temp = t_temp[:4] + "." + t_temp[4:6] + "." + t_temp[6:8]
                wd_temp = struct.unpack("<Q", raw_file[match.regs[0][1] - 9:match.regs[0][1] - 1])[0]
            else:
                t_temp = ''
                wd_temp = ''
            # Append date, wd to time range list
            self.time_range.append([wd_temp, t_temp])
        # Sort the time range list by wd
        self.time_range = sorted(self.time_range, key=self.get_key)

        # Call the time range builder to rebuild time range
        self.build_time_range()

    def get_key(self, item):
        """
        Return the key in the time range item provided.
        """
        return item[0]

    def build_time_range(self):
        """
        Rebuilds the time range list to
        include the previous and current working descriptor
        as well as the previous and current date found
        """
        prev_date = '0'
        prev_wd = 0
        temp = []

        # Iterate through each in time range list
        for i in self.time_range:
            # Len is 7 when prev_date is 'Unknown'
            if len(prev_date) == 7:
                p_date = 0
                c_date = i[1][:10].replace(".", "")
            # When current date is 'Unknown'
            if len(i[1]) == 7:
                p_date = prev_date[:10].replace(".", "")
                c_date = 0
            # When both dates are known
            if len(prev_date) != 7 and len(i[1]) != 7:
                p_date = prev_date[:10].replace(".", "")
                c_date = i[1][:10].replace(".", "")
            # Bypass a date when current date is less than prev date
            if int(c_date) < int(p_date):
                prev_wd = prev_wd
                prev_date = prev_date
            else:
                # Reassign prev_date to 'Unknown'
                if prev_date == '0':
                    prev_date = 'Unknown'
                # Add previous, current wd and previous, current date to temp
                temp.append([prev_wd, i[0], prev_date, i[1]])
                prev_wd = i[0]
                prev_date = i[1]
        # Assign temp list to time range list
        self.time_range = temp

    def find_page_records(self, page_buf, page_start_off, trace):
        """
        Input values are starting offset of current page and
        end offset of current page within the current fsevent file
        find_page_records will identify all records within a given page.
        """

        # Initialize variables
        fullpath = ''
        char = ''

        # Start, end offset of first record to be parsed within current DLS page
        start_offset = 12
        end_offset = 13

        len_buf = len(page_buf)

        # Call the file header parser for current DLS page
        try:
            FsEventFileHeader(
                page_buf[:13],
                self.src_fullpath
            )
        except:
            log.warning(f"{self.src_filename}\tError: Unable to parse file header at offset {page_start_off}\n")

        # Account for length of record for different DLS versions
        # Prior to HighSierra
        if self.dls_version == 1:
            bin_len = 13
            rbin_len = 12
        # HighSierra
        elif self.dls_version == 2:
            bin_len = 21
            rbin_len = 20
        else:
            pass

        # Iterate through the page.
        # Valid record check should be true while parsing.
        # If an invalid record is encounted (occurs in carved gzips)
        # parsing stops for the current file
        while len_buf > start_offset and self.valid_record_check:
            # Grab the first char
            char = page_buf[start_offset:end_offset].hex()

            if char != '00':
                # Replace non-printable char with nothing
                if str(char).lower() == '0d' or str(char).lower() == '0a':
                    log.debug(f'{self.src_filename}\tInfo: Non-printable char {char} in record fullpath at '
                          f'page offset {page_start_off + start_offset}. Parser removed char for reporting '
                          'purposes.\n')
                    char = ''
                # Append the current char to the full path for current record
                fullpath = fullpath + char
                # Increment the offsets by one
                start_offset += 1
                end_offset += 1
                # Continue the while loop
                continue
            elif char == '00':
                # When 00 is found, then it is the end of fullpath
                # Increment the offsets by bin_len, this will be the start of next full path
                start_offset += bin_len
                end_offset += bin_len

            # Decode fullpath that was stored as hex
            fullpath = bytes.fromhex(fullpath).decode("utf-8").replace('\t', '')
            # Store the record length
            record_len = len(fullpath) + bin_len

            # Account for records that do not have a fullpath
            if record_len == bin_len:
                # Assign NULL as the path
                fullpath = "NULL"

            # Assign raw record offsets #
            r_start = start_offset - rbin_len
            r_end = start_offset

            # Strip raw record from page buffer #
            raw_record = page_buf[r_start:r_end]

            # Strip mask from buffer and encode as hex #
            mask_hex = "0x" + raw_record[8:12].hex()

            # Account for carved files when record end offset
            # occurs after the length of the buffer
            if r_end > len_buf:
                continue

            # Set fs_node_id to empty for DLS version 1
            # Prior to HighSierra
            if self.dls_version == 1:
                fs_node_id = ""
            # Assign file system node id if DLS version is 2
            # Introduced with HighSierra
            if self.dls_version == 2:
                fs_node_id = struct.unpack("<q", raw_record[12:])[0]

            record_off = start_offset + page_start_off

            record = FSEventRecord(raw_record, record_off)

            # Check record to see if is valid. Identifies invalid/corrupted
            # that sometimes occur in carved gzip files
            self.valid_record_check = self.check_record(record.mask)

            # If record is not valid, stop parsing records in page
            if self.valid_record_check is False or record.wd == 0:
                log.warning(f'{self.src_filename}\tInfo: First invalid record found in carved '
                      'gzip at offset {page_start_off + start_offset}. The remainder of this buffer '
                      'will not be parsed.\n')
                break
            # Otherwise assign attributes and add to outpur reports
            else:
                f_path, f_name = os.path.split(fullpath)
                dates = self.apply_date(record.wd)
                # Assign our current records attributes
                attributes = {
                    'id': record.wd,
                    'id_hex': record.wd_hex.decode("ascii") + " (" + str(record.wd) + ")",
                    'fullpath': fullpath,
                    'filename': f_name,
                    'type': record.mask[0],
                    'flags': record.mask[1],
                    'approx_dates_plus_minus_one_day': dates,
                    'mask': mask_hex,
                    'node_id': fs_node_id,
                    'record_end_offset': record_off,
                    'source': self.src_fullpath,
                    'source_modified_time': self.m_time
                }

                builder = trace.child_builder()
                # add properties to our new trace to be, be sure to set the name (which is required)
                builder.update({
                    'name': record.wd_hex.decode("ascii") + " (" + str(record.wd) + ")",
                    'event.application': 'fseventsd',
                    'event.createdOn': self.m_time,
                    'event.type': record.mask[0],
                    'event.text': f_name
                    # .update() will return the builder, allowing a chained .build() call
                    # to actually save the new trace
                }).build()

                fullpath = ''
            # Increment the current record count by 1
            self.all_records_count += 1

    def check_record(self, mask):
        """
        Checks for conflicts in the record's flags
        to determine if the record is valid to limit the
        number of invalid records in parsed output.
        Applies only to carved gzip
        """
        if self.is_carved_gzip:
            decode_error = False
            # Flag conflicts
            # These flag combinations can not exist together
            type_err = "FolderEvent" in mask[0] and "FileEvent" in mask[0]
            fol_cr_err = "FolderEvent" in mask[0] and "Created" in mask[1] and \
                         "FolderCreated" not in mask[1]
            fil_cr_err = "FileEvent" in mask[0] and "FolderCreated" in mask[1]
            lnk_err = "SymbolicLink" in mask[0] and "HardLink" in mask[0]
            h_lnk_err = "HardLink" not in mask[0] and "LastHardLink" in mask[1]
            h_lnk_err_2 = "LastHardLink" in mask[1] and ";Removed" not in mask[1]
            n_used_err = "NOT_USED-0x0" in mask[1]
            ver_error = "ItemCloned" in mask[1] and self.dls_version == 1

            # If any error exists return false to caller
            if type_err or \
                    fol_cr_err or \
                    fil_cr_err or \
                    lnk_err or \
                    h_lnk_err or \
                    h_lnk_err_2 or \
                    n_used_err or \
                    decode_error or \
                    ver_error:
                return False
            else:
                # Record passed tests and may be valid
                # return true so that record is included in output reports
                return True
        else:
            # Return true. fsevent file was not identified as being carved
            return True


    def apply_date(self, wd):
        """
        Applies the approximate date to
        the current record by comparing thewd
        to what is stored in the time range list.
        """
        t_range_count = len(self.time_range)
        count = 1
        c_mod_date = str(self.m_time)[:10].replace("-", ".")

        # No dates were found. Return source mod date
        if len(self.time_range) == 0 and not self.is_carved_gzip and self.use_file_mod_dates:
            return c_mod_date
        # If dates were found
        elif len(self.time_range) != 0 and not self.is_carved_gzip:

            # Iterate through the time range list
            # and assign the time range based off the
            # wd/record event id.
            for i in self.time_range:
                # When record id falls between the previous
                # id and the current id within the time range list
                if wd > i[0] and wd < i[1]:
                    # When the previous date is the same as current
                    if i[2] == i[3]:
                        return i[2]
                    # Otherwise return the date range
                    else:
                        return i[2] + " - " + i[3]
                # When event id matches previous wd in list
                # assign previous date
                elif wd == i[0]:
                    return str(i[2])
                # When event id matches current wd in list
                # assign current date
                elif wd == i[1]:
                    return str(i[3])
                # When the event id is greater than the last in list
                # assign return source mod date
                elif count == t_range_count and wd >= i[1] and self.use_file_mod_dates:
                    return c_mod_date
                else:
                    count = count + 1
                    continue
        else:
            return "Unknown"


class FsEventFileHeader:
    """
    FSEvent file header structure.
        Each page within the decompressed begins with DLS1 or DLS2
        It is stored using a byte order of little-endian.
    """

    def __init__(self, buf, filename):
        """
        """
        # Name and path of current source fsevent file
        self.src_fullpath = filename
        # Page header 'DLS1' or 'DLS2'
        # Was written to disk using little-endian
        # Byte stream contains either "1SLD" or "2SLD", reversing order
        self.signature = buf[4] + buf[3] + buf[2] + buf[1]
        # Unknown raw values in DLS header
        # self.unknown_raw = buf[4:8]
        # Unknown hex version
        # self.unknown_hex = buf[4:8].encode("hex")
        # Unknown integer version
        # self.unknown_int = struct.unpack("<I", self.unknown_raw)[0]
        # Size of current DLS page
        self.filesize = struct.unpack("<I", buf[8:12])[0]


class FSEventRecord(dict):
    """
    FSEvent record structure.
    """
    def __init__(self, buf, offset):
        """
        """
        # Offset of the record within the fsevent file
        self.file_offset = offset
        # Raw record hex version
        self.header_hex = binascii.b2a_hex(buf)
        # Record wd or event id
        self.wd = struct.unpack("<Q", buf[0:8])[0]
        # Record wd_hex
        wd_buf = bytearray(buf[0:8])
        wd_buf.reverse()
        self.wd_hex = binascii.b2a_hex(wd_buf)
        # Enumerate mask flags, string version
        self.mask = enumerate_flags(
            struct.unpack(">I", buf[8:12])[0],
            EVENTMASK
        )


class FSEventsPlugin(ExtractionPlugin):
    """
    ExtractionPlugin implementation parsing apple fsevent logs
    """
    def __init__(self):
        self._handler = FSEventHandler()

    def plugin_info(self) -> PluginInfo:
        log.info('pluginInfo request')
        plugin_info = PluginInfo(id=PluginId('nfi.nl', 'events', 'FSEventsPluginPython'),
                                 version='1.0.0',
                                 description='Example Extraction Plugin: Parses apple filesystem events',
                                 author=Author('MoDS', 'mods@holmes.nl', 'NFI'),
                                 maturity=MaturityLevel.PROOF_OF_CONCEPT,
                                 webpage_url='https://hansken.org',
                                 # TODO: HANSKEN-14840: FSEvents plugin matcher fails to match on file.path property
                                 # TODO: matcher='file.path:fseventsd AND data.raw.fileType:GnuZip AND $data.type=raw'
                                 matcher='data.raw.fileType:GnuZip AND $data.type=raw',
                                 license='Apache License 2.0'
                                 )
        log.debug(f'returning plugin info: {plugin_info}')
        return plugin_info

    def process(self, trace: AbstractTrace, data_context: DataContext):
        self._handler.parse_event(trace)
