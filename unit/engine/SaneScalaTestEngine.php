<?php

final class SaneScalaTestEngine extends ArcanistUnitTestEngine {

  public function runAllTests() {
    return true;
  }

  public function shouldEchoTestResults() {
    return true;
  }

  public function supportsRunAllTests() {
    return true;
  }

  public function run() {
    $project_root = $this->getWorkingCopy()->getProjectRoot();
    $reports_dir = 'target/test-reports';
    $coverage_file = 'target/scala-2.12/coverage-report/cobertura.xml';
    $clear_reports_cmd = csprintf(
      'cd %s; rm -rf %s;',
      $project_root,
      $reports_dir
    );
    
    $test_cmd = $clear_reports_cmd.'sbt coverage test coverageReport;';
    $this->execCmd($test_cmd);

    $all_results = array();
    $output_dir = $project_root.'/'.$reports_dir;
    $handle = opendir($output_dir);

    $coverage_report = $this->readCoverageReport(
      $project_root.'/'.$coverage_file
    );

    while (false !== ($entry = readdir($handle))) {
      if (preg_match('@TEST@', $entry)) {
        $parser = new ArcanistXUnitTestResultParser();
        $results = $parser->parseTestResults(
          Filesystem::readFile($output_dir.'/'.$entry)
        );
        foreach ($results as $result) {
          $result->setCoverage($coverage_report);
        }
        $all_results = array_merge($all_results, $results);
      }
    }

    $this->execCmd($clear_reports_cmd);
    return $all_results;
  }

  private function execCmd($cmd) {
    $console = PhutilConsole::getConsole();
    $future = new ExecFuture('%C', $cmd);
    try {
      $future->resolvex();
    } catch (CommandException $e) {
      $console->writeErr($cmd.' failed: %s', $e->getStdout());
      exit(1);
    }
  }

  private function readCoverageReport($path) {
    $coverage_data = Filesystem::readFile($path);
    if (empty($coverage_data)) {
       return array();
    }

    $coverage_dom = new DOMDocument();
    $coverage_dom->loadXML($coverage_data);

    $paths = $this->getPaths();
    $reports = array();
    $classes = $coverage_dom->getElementsByTagName('class');

    foreach ($classes as $class) {
      // filename as mentioned in the report is relative to `src/main/scala/`,
      // but it needs to be relative to the project root.
      $relative_path = 'src/main/scala/'.$class->getAttribute('filename');
      $absolute_path = Filesystem::resolvePath($relative_path);

      // skip reporting coverage for files that aren't in the diff
      if (!in_array($relative_path, $paths)) continue;

      if (!file_exists($absolute_path)) continue;

      // get total line count in file
      $line_count = count(file($absolute_path));

      // Each line gets one letter in the coverage string.
      // $coverage_str[i] represents the coverage state of line i+1.
      // The letter 'N' means non executable, 'C' means the line has
      // test coverage, while 'U' means that the line doesn't have
      // test coverage.
      $NON_EXEC = 'N';
      $COVERED = 'C';
      $NOT_COVERED = 'U';

      // Start out by treating each line as non-executable.
      $coverage_str = str_repeat($NON_EXEC, $line_count);

      $lines = $class->getElementsByTagName('line');
      foreach ($lines as $line) {
        $line_no = (int)$line->getAttribute('number');
        $has_coverage = (((int)$line->getAttribute('hits')) != 0);
        if ($has_coverage) {
          $coverage_str[$line_no - 1] = $COVERED;
        }
        // Mark the line uncovered only if
        // it wasn't already marked as covered
        else if ($coverage_str[$line_no - 1] != $COVERED) {
          $coverage_str[$line_no - 1] = $NOT_COVERED;
        }
      }
      $reports[$relative_path] = $coverage_str;
    }

    return $reports;
  }
}
