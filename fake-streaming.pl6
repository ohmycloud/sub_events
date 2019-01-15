sub MAIN(Str :$host = '0.0.0.0', Int :$port = 3333) {

    my $vin = 'LSJA0000000000091';
    my $last_meter = 0;

    my @temp = ([0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0 ] xx *).flat;
    my @high = ([1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1 ] xx *).flat; #  my @high = ([1, 1, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1 ] xx *).flat;
    my @esd  = ([1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 1 ] xx *).flat; # ([1, 0, 1, 1, 0, 0, 0, 1, 1, 0, 0, 1 ] xx *).flat;

    react {
        whenever IO::Socket::Async.listen($host, $port) -> $conn {
            react {

                whenever Supply.interval(5) { # 每 5 秒打印一次
                    print sprintf("\{'vin':'%s','ts':%s,'veh_odo':%s,'alm_common_temp_diff':%s,'alm_common_temp_high':%s,'alm_common_esd_high':%s}\n", $vin, DateTime.now.posix * 1000, $last_meter, @temp[$++], @high[$++], @esd[$++]);
                    $conn.print: sprintf("\{'vin':'%s','ts':%s,'veh_odo':%s,'alm_common_temp_diff':%s,'alm_common_temp_high':%s,'alm_common_esd_high':%s}\n", $vin, DateTime.now.posix * 1000, $last_meter++, @temp[$++], @high[$++], @esd[$++]);
                }

                whenever signal(SIGINT) {
                    say "Done.";
                    done;
                }
            }
        }
        CATCH {
            default {
                say .^name, ': ', .Str;
                say "handled in $?LINE";
            }
        }
    }
}